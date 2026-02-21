/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.dahuadoor.internal.handler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.dahuadoor.internal.DahuaBridgeConfiguration;
import org.openhab.binding.dahuadoor.internal.DahuaDoorHttpQueries;
import org.openhab.binding.dahuadoor.internal.dahuaeventhandler.DHIPEventListener;
import org.openhab.binding.dahuadoor.internal.dahuaeventhandler.DahuaEventClient;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The {@link DahuaBridgeHandler} manages the connection to Dahua devices and routes events
 * to child device handlers.
 *
 * @author Sven Schad - Initial contribution
 */
@NonNullByDefault
public class DahuaBridgeHandler extends BaseBridgeHandler implements DHIPEventListener {

    private final Logger logger = LoggerFactory.getLogger(DahuaBridgeHandler.class);
    private final Gson gson = new Gson();
    private final Set<DahuaBaseThingHandler> childHandlers = Collections.synchronizedSet(new HashSet<>());

    private @Nullable DahuaBridgeConfiguration config;
    private @Nullable DahuaEventClient client;
    private @Nullable DahuaDoorHttpQueries queries;
    private @Nullable HttpClient httpClient;

    public DahuaBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public @Nullable HttpClient getHttpClient() {
        return this.httpClient;
    }

    public @Nullable DahuaDoorHttpQueries getQueries() {
        return this.queries;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Bridge has no channels
    }

    @Override
    public void initialize() {
        config = getConfigAs(DahuaBridgeConfiguration.class);

        // Validate required configuration
        if (config.hostname == null || config.hostname.isBlank() || config.username == null || config.username.isBlank()
                || config.password == null || config.password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Hostname, username and password must be configured.");
            return;
        }

        client = new DahuaEventClient(config.hostname, config.username, config.password, this, scheduler,
                this::errorInformer);
        queries = new DahuaDoorHttpQueries(httpClient, config);

        // Mark bridge as online; errorInformer will switch to OFFLINE on failures
        updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, "Connected to device");
    }

    @Override
    public void dispose() {
        if (client != null) {
            client.dispose();
            client = null;
        }
        if (queries != null) {
            queries.dispose();
            queries = null;
        }
        childHandlers.clear();
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        super.childHandlerInitialized(childHandler, childThing);
        if (childHandler instanceof DahuaBaseThingHandler handler) {
            synchronized (childHandlers) {
                childHandlers.add(handler);
                logger.debug("Child handler initialized: {}", childThing.getUID());
            }
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof DahuaBaseThingHandler handler) {
            synchronized (childHandlers) {
                childHandlers.remove(handler);
                logger.debug("Child handler disposed: {}", childThing.getUID());
            }
        }
        super.childHandlerDisposed(childHandler, childThing);
    }

    /**
     * Get the bridge configuration.
     *
     * @return The bridge configuration, or null if not initialized
     */
    public @Nullable DahuaBridgeConfiguration getBridgeConfiguration() {
        return config;
    }

    public void errorInformer(String msgError) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, msgError);
    }

    @Override
    public void eventHandler(JsonObject data) {
        // Set bridge ONLINE when first event is received (confirms successful connection)
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }

        try {
            logger.trace("JSON{}", data);
            JsonObject jsonObj = data.getAsJsonObject("params");
            JsonArray firstLevel = jsonObj.getAsJsonArray("eventList");
            JsonObject eventList = firstLevel.get(0).getAsJsonObject();
            String eventCode = eventList.get("Code").getAsString();
            JsonObject eventData = eventList.getAsJsonObject("Data");

            if (firstLevel.size() > 1) {
                logger.debug("Event Manager subscription reply: {}", eventCode);
            } else {
                switch (eventCode) {
                    case "CallNoAnswered":
                        handleVTOCall();
                        break;
                    case "IgnoreInvite":
                        handleVTHAnswer();
                        break;
                    case "VideoMotion":
                        handleMotionEvent();
                        break;
                    case "RtspSessionDisconnect":
                        handleRTSPDisconnect(eventList, eventData);
                        break;
                    case "BackKeyLight":
                        handleBackKeyLight(eventData);
                        break;
                    case "TimeChange":
                        handleTimeChange(eventData);
                        break;
                    case "NTPAdjustTime":
                        handleNTPAdjust(eventData);
                        break;
                    case "KeepLightOn":
                        handleKeepLightOn(eventData);
                        break;
                    case "VideoBlind":
                        handleVideoBlind(eventList);
                        break;
                    case "FingerPrintCheck":
                        handleFingerPrintCheck(eventData);
                        break;
                    case "DoorCard":
                        handleDoorCard(eventList, eventData);
                        break;
                    case "SIPRegisterResult":
                        handleSIPRegisterResult(eventList, eventData);
                        break;
                    case "AccessControl":
                        handleAccessControl(eventData);
                        break;
                    case "CallSnap":
                        handleCallSnap(eventData);
                        break;
                    case "HungupPhone":
                    case "HangupPhone":
                        handleHangupPhone(eventList, eventData);
                        break;
                    case "Hangup":
                        handleHangup(eventList, eventData);
                        break;
                    case "Invite":
                        handleInvite(eventList, eventData);
                        break;
                    case "AlarmLocal":
                        handleAlarmLocal(eventList, eventData);
                        break;
                    case "AccessSnap":
                        handleAccessSnap(eventData);
                        break;
                    case "RequestCallState":
                        handleRequestCallState(eventList, eventData);
                        break;
                    case "PassiveHangup":
                        handlePassiveHangup(eventList, eventData);
                        break;
                    case "ProfileAlarmTransmit":
                        handleProfileAlarmTransit(eventList, eventData);
                        break;
                    case "NewFile":
                        handleNewFile(eventList, eventData);
                        break;
                    case "UpdateFile":
                        handleUpdateFile(eventList, eventData);
                        break;
                    case "Reboot":
                        handleReboot(eventList, eventData);
                        break;
                    case "SecurityImExport":
                        handleSecurityImport(eventList, eventData);
                        break;
                    case "DGSErrorReport":
                        handleDGSErrorReport(eventList, eventData);
                        break;
                    case "Upgrade":
                        handleUpgrade(eventList, eventData);
                        break;
                    case "SendCard":
                        handleSendCard(eventList, eventData);
                        break;
                    case "AddCard":
                        handleAddCard(eventList, eventData);
                        break;
                    case "DoorStatus":
                        handleDoorStatus(eventList, eventData);
                        break;
                    case "DoorControl":
                        handleDoorControl(eventList, eventData);
                        break;
                    case "DoorNotClosed":
                        handleDoorNotClosed(eventList, eventData);
                        break;
                    case "NetworkChange":
                        handleNetworkChanged(eventList, eventData);
                        break;
                    default:
                        logger.debug("Unknown event received. JSON{}", gson.toJson(data));
                }
            }
        } catch (Exception e) {
            String rawPayload = (data != null) ? data.toString() : "null";
            logger.debug("Exception while handling DahuaDoor event. Raw payload: {}", rawPayload, e);
        }
    }

    // Event handler methods - log events and route to child things where appropriate
    private void handleVTOCall() {
        logger.debug("Event Call from VTO");
        notifyChildrenButtonPressed(0); // Button 0 for CallNoAnswered event
    }

    private void handleInvite(JsonObject eventList, JsonObject eventData) {
        String lockNum = eventData.has("LockNum") ? eventData.get("LockNum").getAsString() : "0";
        logger.debug("Event: Invite, Action {}, CallID {}, Lock Number {}", eventList.get("Action").getAsString(),
                eventData.get("CallID").getAsString(), lockNum);

        // Parse lock number and notify appropriate child
        try {
            int buttonNumber = Integer.parseInt(lockNum);
            notifyChildrenButtonPressed(buttonNumber);
        } catch (NumberFormatException e) {
            logger.warn("Could not parse lock number: {}", lockNum);
        }
    }

    private void notifyChildrenButtonPressed(int buttonNumber) {
        synchronized (childHandlers) {
            for (DahuaBaseThingHandler handler : childHandlers) {
                handler.onButtonPressed(buttonNumber);
            }
        }
    }

    private void handleVTHAnswer() {
        logger.debug("Event VTH answered call from VTO");
    }

    private void handleMotionEvent() {
        logger.debug("Event VideoMotion");
    }

    private void handleRTSPDisconnect(JsonObject eventList, JsonObject eventData) {
        if ("Start".equals(eventList.get("Action").getAsString())) {
            logger.debug("Event Rtsp-Session from {} disconnected",
                    eventData.get("Device").getAsString().replace("::ffff:", ""));
        } else if ("Stop".equals(eventList.get("Action").getAsString())) {
            logger.debug("Event Rtsp-Session from {} connected",
                    eventData.get("Device").getAsString().replace("::ffff:", ""));
        }
    }

    private void handleBackKeyLight(JsonObject eventData) {
        logger.debug("Event BackKeyLight with State {} ", eventData.get("State").getAsString());
    }

    private void handleTimeChange(JsonObject eventData) {
        logger.debug("Event TimeChange, BeforeModifyTime: {}, ModifiedTime: {}",
                eventData.get("BeforeModifyTime").getAsString(), eventData.get("ModifiedTime").getAsString());
    }

    private void handleNTPAdjust(JsonObject eventData) {
        if (eventData.get("result").getAsBoolean()) {
            logger.debug("Event NTPAdjustTime with {} success", eventData.get("Address").getAsString());
        } else {
            logger.debug("Event NTPAdjustTime failed");
        }
    }

    private void handleKeepLightOn(JsonObject eventData) {
        if ("On".equals(eventData.get("Status").getAsString())) {
            logger.debug("Event KeepLightOn");
        } else if ("Off".equals(eventData.get("Status").getAsString())) {
            logger.debug("Event KeepLightOff");
        }
    }

    private void handleVideoBlind(JsonObject eventList) {
        if ("Start".equals(eventList.get("Action").getAsString())) {
            logger.debug("Event VideoBlind started");
        } else if ("Stop".equals(eventList.get("Action").getAsString())) {
            logger.debug("Event VideoBlind stopped");
        }
    }

    private void handleFingerPrintCheck(JsonObject eventData) {
        if (eventData.get("FingerPrintID").getAsInt() > -1) {
            int finger = eventData.get("FingerPrintID").getAsInt();
            logger.debug("Event FingerPrintCheck success, Finger number {}, User {}", finger, "User" + finger);
        } else {
            logger.debug("Event FingerPrintCheck failed, unknown Finger");
        }
    }

    private void handleDoorCard(JsonObject eventList, JsonObject eventData) {
        if ("Pulse".equals(eventList.get("Action").getAsString())) {
            logger.debug("DoorCard {} was used at door", eventData.get("Number").getAsString());
        }
    }

    private void handleSIPRegisterResult(JsonObject eventList, JsonObject eventData) {
        if ("Pulse".equals(eventList.get("Action").getAsString())) {
            if (eventData.get("Success").getAsBoolean()) {
                logger.debug("Event SIPRegisterResult, Success");
            } else {
                logger.debug("Event SIPRegisterResult, Failed");
            }
        }
    }

    private void handleAccessControl(JsonObject eventData) {
        logger.debug("Event: AccessControl, Name {}, Method {}, ReaderID {}, UserID {}",
                eventData.get("Name").getAsString(), eventData.get("Method").getAsString(),
                eventData.get("ReaderID").getAsString(), eventData.get("UserID").getAsString());
    }

    private void handleCallSnap(JsonObject eventData) {
        logger.debug("Event: CallSnap, DeviceType {}, RemoteID {}, RemoteIP {}, CallStatus {}",
                eventData.get("DeviceType").getAsString(), eventData.get("RemoteID").getAsString(),
                eventData.get("RemoteIP").getAsString(),
                eventData.getAsJsonArray("ChannelStates").get(0).getAsString());
    }

    private void handleHangupPhone(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: HungupPhone (handled as HangupPhone), Action {}, LocaleTime {}",
                eventList.get("Action").getAsString(), eventData.get("LocaleTime").getAsString());
    }

    private void handleHangup(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: Hangup, Action {}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("LocaleTime").getAsString());
    }

    private void handleAlarmLocal(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: AlarmLocal, Action {}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("LocaleTime").getAsString());
    }

    private void handleAccessSnap(JsonObject eventData) {
        logger.debug("Event: AccessSnap, FTP upload to {}", eventData.get("FtpUrl").getAsString());
    }

    private void handleRequestCallState(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: RequestCallState, Action {}, LocaleTime {}, Index {}",
                eventList.get("Action").getAsString(), eventData.get("LocaleTime").getAsString(),
                eventData.get("Index").getAsString());
    }

    private void handlePassiveHangup(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: PassiveHangup, Action {}, LocaleTime {}, Index {}", eventList.get("Action").getAsString(),
                eventData.get("LocaleTime").getAsString(), eventData.get("Index").getAsString());
    }

    private void handleProfileAlarmTransit(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: ProfileAlarmTransmit, Action {}, AlarmType {}, DevSrcType {}, SenseMethod {}",
                eventList.get("Action").getAsString(), eventData.get("AlarmType").getAsString(),
                eventData.get("DevSrcType").getAsString(), eventData.get("SenseMethod").getAsString());
    }

    private void handleNewFile(JsonObject eventList, JsonObject eventData) {
        String action = eventList.has("Action") ? eventList.get("Action").getAsString() : "unknown";
        String file = eventData.has("File") ? eventData.get("File").getAsString() : "unknown";
        String folder = eventData.has("Filter") ? eventData.get("Filter").getAsString() : "unknown";
        String localeTime = eventData.has("LocaleTime") ? eventData.get("LocaleTime").getAsString() : "unknown";
        String index = eventList.has("Index") ? eventList.get("Index").getAsString() : "unknown";

        logger.debug("Event: NewFile, Action {}, File {}, Folder {}, LocaleTime {}, Index {}", action, file, folder,
                localeTime, index);
    }

    private void handleUpdateFile(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: UpdateFile, Action {}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("LocaleTime").getAsString());
    }

    private void handleReboot(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: Reboot, Action {}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("LocaleTime").getAsString());
    }

    private void handleSecurityImport(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: SecurityImExport, Action {}, LocaleTime {}, Status {}",
                eventList.get("Action").getAsString(), eventData.get("LocaleTime").getAsString(),
                eventData.get("Status").getAsString());
    }

    private void handleDGSErrorReport(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: DGSErrorReport, Action {}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("LocaleTime").getAsString());
    }

    private void handleUpgrade(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: Upgrade, Action {}, with State{}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("State").getAsString(), eventData.get("LocaleTime").getAsString());
    }

    private void handleSendCard(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: SendCard, Action {}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("LocaleTime").getAsString());
    }

    private void handleAddCard(JsonObject eventList, JsonObject eventData) {
        JsonObject cardData = eventData.getAsJsonArray("Data").get(0).getAsJsonObject();
        logger.debug(
                "Event: AddCard, Action {}: CardNo {}, UserID {}, UserName {}, CardStatus {}, CardType {}, Doors: Door 0={}, Door1={}",
                eventList.get("Action").getAsString(), cardData.get("CardNo").getAsString(),
                cardData.get("UserID").getAsString(), cardData.get("UserName").getAsString(),
                cardData.get("CardStatus").getAsString(), cardData.get("CardType").getAsString(),
                cardData.getAsJsonArray("Doors").get(0).getAsString(),
                cardData.getAsJsonArray("Doors").get(1).getAsString());
    }

    private void handleDoorStatus(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: DoorStatus, Action {}, Status: {}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("Status").getAsString(), eventData.get("LocaleTime").getAsString());
    }

    private void handleDoorControl(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: DoorControl, Action {}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("LocaleTime").getAsString());
    }

    private void handleDoorNotClosed(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: DoorNotClosed, Action {}, Name{}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("Name").getAsString(), eventData.get("LocaleTime").getAsString());
    }

    private void handleNetworkChanged(JsonObject eventList, JsonObject eventData) {
        logger.debug("Event: NetworkChange, Action {}, LocaleTime {}", eventList.get("Action").getAsString(),
                eventData.get("LocaleTime").getAsString());
    }
}
