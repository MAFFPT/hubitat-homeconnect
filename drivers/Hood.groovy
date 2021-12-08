/**
 *  Copyright 2021
 *
 *  Based on the original work done by https://github.com/Wattos/hubitat
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Home Connect Hood (Child Device of Home Conection Integration)
 *
 *  Author: Rangner Ferraz Guimaraes (rferrazguimaraes)
 *  Date: 2021-11-28
 *  Version: 1.0 - Initial commit
 */

import groovy.transform.Field
import groovy.json.JsonSlurper

#include rferrazguimaraes.HomeConnect

@Field Utils = Utils_create();
@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]
@Field static final Integer eventStreamDisconnectGracePeriod = 30
def driverVer() { return "1.0" }

metadata {
    definition(name: "Home Connect Hood", namespace: "rferrazguimaraes", author: "Rangner Ferraz Guimaraes") {
        capability "Sensor"
        capability "Switch"
        capability "Initialize"
        
        command "deviceLog", [[name: "Level*", type:"STRING", description: "Level of the message"], 
                              [name: "Message*", type:"STRING", description: "Message"]] 
        //command "connectEventStream"
        //command "disconnectEventStream"
        command "startProgram"
        command "stopProgram"
        //command "reset"

        attribute "AvailableProgramsList", "JSON_OBJECT"
        attribute "AvailableOptionsList", "JSON_OBJECT"

        // BSH.Common.Status.RemoteControlActive
        // This status indicates whether the allowance for remote controlling is enabled.
        attribute "RemoteControlActive", "enum", ["true", "true"]

        // BSH.Common.Status.RemoteControlStartAllowed
        // This status indicates whether the remote program start is enabled. 
        // This can happen due to a programmatic change (only disabling), 
        // or manually by the user changing the flag locally on the home appliance, 
        // or automatically after a certain duration - usually 24 hours.
        attribute "RemoteControlStartAllowed", "enum", ["true", "false"]

        // BSH.Common.Status.OperationState
        // This status describes the operation state of the home appliance. 
        attribute "OperationState", "enum", [
            // Key: BSH.Common.EnumType.OperationState.Inactive
            // Description: Home appliance is inactive. It could be switched off or in standby.
            "Inactive",

            // Key: BSH.Common.EnumType.OperationState.Ready
            // Description: Home appliance is switched on. No program is active.
            "Ready",

            // Key: BSH.Common.EnumType.OperationState.DelayedStart
            // Description: A program has been activated but has not started yet.
            "DelayedStart",

            // Key: BSH.Common.EnumType.OperationState.Run
            // Description: A program is currently active.
            "Run",

            // Key: BSH.Common.EnumType.OperationState.Pause
            // Description: The active program has been paused.
            "Pause",

            // Key: BSH.Common.EnumType.OperationState.ActionRequired
            // Description: The active program requires a user interaction.
            "ActionRequired",

            // Key: BSH.Common.EnumType.OperationState.Finished
            // Description: The active program has finished or has been aborted successfully.
            "Finished",

            // Key: BSH.Common.EnumType.OperationState.Error
            // Description: The home appliance is in an error state.
            "Error",

            // Key: BSH.Common.EnumType.OperationState.Aborting
            // Description: The active program is currently aborting.
            "Aborting",
        ]

        // BSH.Common.Status.DoorState
        // This status describes the state of the door of the home appliance. 
        // A change of that status is either triggered by the user operating 
        // the home appliance locally (i.e. opening/closing door) or 
        // automatically by the home appliance (i.e. locking the door).
        //
        // Please note that the door state of coffee machines is currently 
        // only available for American coffee machines. 
        // All other coffee machines will be supported soon.
        attribute "DoorState", "enum", [
            //  Key: BSH.Common.EnumType.DoorState.Open
            // Description: The door of the home appliance is open.
            "Open",

            // Key: BSH.Common.EnumType.DoorState.Closed
            // Description: The door of the home appliance is closed but not locked.
            "Closed",

            //  Key: BSH.Common.EnumType.DoorState.Locked
            // Description: The door of the home appliance is locked.
            "Locked",
        ]

        attribute "ActiveProgram", "string"
        attribute "SelectedProgram", "string"        

        attribute "PowerState", "enum", [
            // Key: BSH.Common.EnumType.PowerState.Off
            // Description: The home appliance switched to off state but can 
            // be switched on by writing the value BSH.Common.EnumType.PowerState.
            // On to this setting.
            "Off",

            // Key: BSH.Common.EnumType.PowerState.On
            // Description: The home appliance switched to on state. 
            // You can switch it off by writing the value BSH.Common.EnumType.PowerState.Off 
            // or BSH.Common.EnumType.PowerState.Standby depending on what is supported by the appliance.
            "On",

            //  Key: BSH.Common.EnumType.PowerState.Standby
            // Description: The home appliance went to standby mode.
            // You can switch it on or off by changing the value of this setting appropriately.
            "Standby"
        ]

        attribute "EventPresentState", "enum", [
            // Key: BSH.Common.EnumType.EventPresentState.Present
            // Description: The event occurred and is present.
            "Event active",

            // Key: BSH.Common.EnumType.EventPresentState.Off
            // Description: The event is off.
            "Off",

            //  Key: BSH.Common.EnumType.EventPresentState.Confirmed
            // Description: The event has been confirmed by the user.
            "Confirmed"
        ]
        
        attribute "EventStreamStatus", "enum", ["connected", "disconnected"]
    }
    
    preferences {
        section { // General
            List<String> availableProgramsList = getAvailableProgramsList()
            if(availableProgramsList.size() != 0)
            {
                input name:"selectedProgram", type:"enum", title: "Select Program", options:availableProgramsList
            }
            
            List<String> availableOptionList = getAvailableOptionsList()
            for(int i = 0; i < availableOptionList.size(); ++i) {
                String titleName = availableOptionList[i]
                String optionName = titleName.replaceAll("\\s","")
                input name:optionName, type:"bool", title: "${titleName}", defaultValue: false 
            }

            input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
        }
    }
}
