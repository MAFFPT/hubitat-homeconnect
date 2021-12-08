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
 *  Home Connect Library (shared code among drivers)
 *
 *  Author: Rangner Ferraz Guimaraes (rferrazguimaraes)
 *  Date: 2021-11-28
 *  Version: 1.0 - Initial commit
 */

library (
    base:              "driver",
    author:            "Rangner Ferraz Guimaraes",
    category:          "driverUtilities",
    description:       "driverUtilities",
    name:              "HomeConnect",
    namespace:         "rferrazguimaraes",
    documentationLink: ""
)

void startProgram() {
    if(selectedProgram != null) {
        def programToSelect = state.foundAvailablePrograms.find { it.name == selectedProgram }
        if(programToSelect) {
            parent.startProgram(device, programToSelect.key)
        }
    }
}

void stopProgram() {
    parent.stopProgram(device);
}

void initialize() {
    Utils.toLogger("debug", "initialize()")
    intializeStatus();
    //runEvery1Minute("intializeStatus")
}

void installed() {
    Utils.toLogger("debug", "installed()")
    updateAvailableProgramList();
    updateAvailableOptionsList();
    intializeStatus();
}

void updated() {
    Utils.toLogger("debug", "updated()")

    setCurrentProgram()    
    updateAvailableOptionsList()
    setCurrentProgramOptions()
}

void uninstalled() {
    disconnectEventStream()
}

void setCurrentProgram() {
    // set current program
    if(selectedProgram != null) {
        def programToSelect = state.foundAvailablePrograms.find { it.name == selectedProgram }
        if(programToSelect) {
            parent.setSelectedProgram(device, programToSelect.key)
        }
    }    
}

void setCurrentProgramOptions() {
    // set current program option    
    List<String> availableOptionList = getAvailableOptionsList()
    for(int i = 0; i < availableOptionList.size(); ++i) {
        String optionTitle = availableOptionList[i]
        String optionName = optionTitle.replaceAll("\\s","")
        bool optionValue = settings."${optionName}"
        parent.setSelectedProgramOption(device, programOption.key, optionValue)
    }
}

void updateAvailableProgramList() {
    state.foundAvailablePrograms = parent.getAvailableProgramList(device)
    Utils.toLogger("debug", "updateAvailableProgramList state.foundAvailablePrograms: ${state.foundAvailablePrograms}")
    def programList = state.foundAvailablePrograms.collect { it.name }
    //def programList = state.foundAvailablePrograms.collect { it.name != null ? it.name : it.key.tokenize('.').last }
    Utils.toLogger("debug", "getAvailablePrograms programList: ${programList}")
    sendEvent(name:"AvailableProgramsList", value: new groovy.json.JsonBuilder(programList), displayed: false)
}

void updateAvailableOptionsList() {
    if(selectedProgram != null) {
        def programToSelect = state.foundAvailablePrograms.find { it.name == selectedProgram }
        if(programToSelect) {
            state.foundAvailableProgramOptions = parent.getAvailableProgramOptionsList(device, programToSelect.key)
            def programOptionsList = state.foundAvailableProgramOptions.collect { it.name }
            sendEvent(name:"AvailableOptionsList", value: new groovy.json.JsonBuilder(programOptionsList), displayed: false)
            Utils.toLogger("debug", "updateAvailableOptionList programOptionsList: ${programOptionsList}")
            return
        }
    }

    state.foundAvailableProgramOptions = []
    sendEvent(name:"AvailableOptionsList", value: [], displayed: false)
}

void reset() {    
    Utils.toLogger("debug", "reset")
    unschedule()
    sendEvent(name: "EventStreamStatus", value: "disconnected", displayed: true, isStateChange: true)
    disconnectEventStream()
}

List<String> getAvailableProgramsList() {
    String json = device?.currentValue("AvailableProgramsList")
    if (json != null) {
        return parseJson(json)
    }
    return []
}

List<String> getAvailableOptionsList() {
    String json = device?.currentValue("AvailableOptionsList")
    if (json != null) {
        return parseJson(json)
    }    
    return []
}

def on() {
    parent.setPowertate(device, true)
}

def off() {
    parent.setPowertate(device, false)
}

void intializeStatus() {
    Utils.toLogger("debug", "Initializing the status of the device")

    parent.intializeStatus(device)
    
    try {
        disconnectEventStream()
        connectEventStream()
    } catch (Exception e) {
        Utils.toLogger("error", "intializeStatus() failed: ${e.message}")
        setEventStreamStatusToDisconnected()
    }
}

void connectEventStream() {
    Utils.toLogger("debug", "connectEventStream()")
    parent.getHomeConnectAPI().connectDeviceEvents(device.deviceNetworkId, interfaces);
}

void reconnectEventStream(Boolean notIfAlreadyConnected = true) {
    Utils.toLogger("debug", "reconnectEventStream(notIfAlreadyConnected=$notIfAlreadyConnected)")
    
    if (device.currentValue("EventStreamStatus") == "connected" && notIfAlreadyConnected) {
        Utils.toLogger("debug", "already connected; skipping reconnection")
    } else {
        //disconnectEventStream()
        connectEventStream()
    }
}

void disconnectEventStream() {
    Utils.toLogger("debug", "disconnectEventStream()")
    parent.getHomeConnectAPI().disconnectDeviceEvents(device.deviceNetworkId, interfaces);
}

void setEventStreamStatusToConnected() {
    Utils.toLogger("debug", "setEventStreamStatusToConnected()")
    unschedule("setEventStreamStatusToDisconnected")
    if (device.currentValue("EventStreamStatus") == "disconnected") { 
        sendEvent(name: "EventStreamStatus", value: "connected", displayed: true, isStateChange: true)
    }
    state.connectionRetryTime = 15
}

void setEventStreamStatusToDisconnected() {
    Utils.toLogger("debug", "setEventStreamStatusToDisconnected()")
    sendEvent(name: "EventStreamStatus", value: "disconnected", displayed: true, isStateChange: true)
    if (state.connectionRetryTime) {
       state.connectionRetryTime *= 2
       if (state.connectionRetryTime > 900) {
          state.connectionRetryTime = 900 // cap retry time at 15 minutes
       }
    } else {
       state.connectionRetryTime = 15
    }
    Utils.toLogger("debug", "reconnecting EventStream in ${state.connectionRetryTime} seconds")
    runIn(state.connectionRetryTime, "reconnectEventStream")
}

void eventStreamStatus(String text) {
    Utils.toLogger("debug", "Received eventstream status message: ${text}")
    def (String type, String message) = text.split(':', 2)
    switch (type) {    
        case 'START':
            setEventStreamStatusToConnected()
            //Utils.toLogger("info", "Event Stream connected")
            break
        
        case 'STOP':
            Utils.toLogger("debug", "eventStreamDisconnectGracePeriod: ${eventStreamDisconnectGracePeriod}")
            runIn(eventStreamDisconnectGracePeriod, "setEventStreamStatusToDisconnected")
            //Utils.toLogger("info", "Event Stream disconnected")
            break

        default:
            Utils.toLogger("error", "Received unhandled Event Stream status message: ${text}")
            runIn(eventStreamDisconnectGracePeriod, "setEventStreamStatusToDisconnected")
            break
    }
}

void parse(String text) {
    Utils.toLogger("debug", "Received eventstream message: ${text}")  
    parent.processMessage(device, text)
}

def deviceLog(level, msg) {
    Utils.toLogger(level, msg)
}

/**
 * Simple utilities for manipulation
 */

def Utils_create() {
    def instance = [:];
    
    instance.toLogger = { level, msg ->
        if (level && msg) {
            Integer levelIdx = LOG_LEVELS.indexOf(level);
            Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel);
            if (setLevelIdx < 0) {
                setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL);
            }
            if (levelIdx <= setLevelIdx) {
                log."${level}" "${device.displayName} ${msg}";
            }
        }
    }

    return instance;
}
