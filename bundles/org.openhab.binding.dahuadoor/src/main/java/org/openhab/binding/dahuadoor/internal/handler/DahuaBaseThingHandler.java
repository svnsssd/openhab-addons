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

import static org.openhab.binding.dahuadoor.internal.DahuaDoorBindingConstants.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.dahuadoor.internal.DahuaBridgeConfiguration;
import org.openhab.binding.dahuadoor.internal.DahuaDoorHttpQueries;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DahuaBaseThingHandler} provides common functionality for all Dahua device handlers.
 * It manages the bridge connection, handles door control commands, and provides snapshot management.
 *
 * @author Sven Schad - Initial contribution
 */
@NonNullByDefault
public abstract class DahuaBaseThingHandler extends BaseThingHandler {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private @Nullable DahuaBridgeHandler bridgeHandler;

    public DahuaBaseThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        // Get bridge handler and validate configuration
        DahuaBridgeHandler bridge = getBridgeHandler();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    "Bridge is not available or not initialized.");
            return;
        }

        // Validate snapshot path from bridge configuration
        DahuaBridgeConfiguration bridgeConfig = bridge.getBridgeConfiguration();
        if (bridgeConfig == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Bridge configuration is not available.");
            return;
        }
        if (bridgeConfig.snapshotpath.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Snapshot path must be configured on the bridge.");
            return;
        }

        // Device-specific initialization
        initializeDevice();

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        disposeDevice();
        this.bridgeHandler = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        DahuaBridgeHandler bridge = getBridgeHandler();
        if (bridge == null) {
            logger.warn("Bridge not available, cannot handle command");
            return;
        }

        DahuaDoorHttpQueries queries = bridge.getQueries();
        if (queries == null) {
            logger.warn("HTTP queries not initialized, cannot handle command");
            return;
        }

        // Handle door control commands (common to all devices)
        switch (channelUID.getId()) {
            case CHANNEL_OPEN_DOOR_1:
                if (command instanceof OnOffType && command == OnOffType.ON) {
                    queries.openDoor(1);
                    updateState(channelUID, OnOffType.OFF);
                }
                break;
            case CHANNEL_OPEN_DOOR_2:
                if (command instanceof OnOffType && command == OnOffType.ON) {
                    queries.openDoor(2);
                    updateState(channelUID, OnOffType.OFF);
                }
                break;
            default:
                // Delegate to device-specific handler
                handleDeviceCommand(channelUID, command);
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            initialize();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    "Bridge is " + bridgeStatusInfo.getStatus());
        }
    }

    /**
     * Get the bridge handler for this device.
     *
     * @return The bridge handler, or null if not available
     */
    protected @Nullable DahuaBridgeHandler getBridgeHandler() {
        if (this.bridgeHandler == null) {
            Bridge bridge = getBridge();
            if (bridge != null) {
                BridgeHandler handler = bridge.getHandler();
                if (handler instanceof DahuaBridgeHandler dahuaBridge) {
                    this.bridgeHandler = dahuaBridge;
                }
            }
        }
        return this.bridgeHandler;
    }

    /**
     * Save a snapshot to disk with timestamp and as latest snapshot.
     *
     * @param buffer The image data
     * @param suffix Optional suffix for the filename (e.g., "-1", "-2" for multi-button devices)
     */
    protected void saveSnapshot(byte @Nullable [] buffer, String suffix) {
        if (buffer == null) {
            logger.warn("cannot save empty buffer");
            return;
        }

        // Get snapshot path from bridge configuration
        DahuaBridgeHandler bridge = getBridgeHandler();
        if (bridge == null) {
            logger.warn("Bridge handler not available");
            return;
        }
        DahuaBridgeConfiguration bridgeConfig = bridge.getBridgeConfiguration();
        if (bridgeConfig == null) {
            logger.warn("Bridge configuration not initialized");
            return;
        }
        if (bridgeConfig.snapshotpath.isEmpty()) {
            logger.warn("Snapshot path is invalid");
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String filename = bridgeConfig.snapshotpath + "/DoorBell" + suffix + "_" + timestamp + ".jpg";

        try (FileOutputStream fos = new FileOutputStream(new File(filename))) {
            fos.write(buffer);
        } catch (Exception e) {
            logger.warn("Could not write image to file '{}', check permissions and path", filename, e);
            return;
        }

        String latestSnapshotFilename = bridgeConfig.snapshotpath + "/Doorbell" + suffix + ".jpg";
        try {
            Files.copy(Paths.get(filename), Paths.get(latestSnapshotFilename), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            logger.warn("Could not copy file from '{}' to '{}', check permissions and path", filename,
                    latestSnapshotFilename, e);
        }
    }

    /**
     * Update an image channel with the provided buffer.
     *
     * @param channelId The channel ID
     * @param buffer The image data
     */
    protected void updateChannelImage(String channelId, byte @Nullable [] buffer) {
        if (buffer == null || buffer.length == 0) {
            updateState(channelId, UnDefType.UNDEF);
            return;
        }
        RawType image = new RawType(buffer, "image/jpeg");
        updateState(channelId, image);
    }

    /**
     * Request an image from the device.
     *
     * @return The image buffer, or null if request failed
     */
    protected byte @Nullable [] requestImage() {
        DahuaBridgeHandler bridge = getBridgeHandler();
        if (bridge == null) {
            logger.warn("Bridge not available, cannot retrieve image");
            return null;
        }

        DahuaDoorHttpQueries queries = bridge.getQueries();
        if (queries == null) {
            logger.warn("HTTP queries not initialized, cannot retrieve image");
            return null;
        }

        return queries.requestImage();
    }

    /**
     * Called when a button is pressed on this device.
     * This method is called by the bridge when it receives a button event.
     *
     * @param buttonNumber The button number (0-based, where 0 is the first/only button)
     */
    public abstract void onButtonPressed(int buttonNumber);

    /**
     * Device-specific initialization logic.
     * Called after common initialization is complete.
     */
    protected abstract void initializeDevice();

    /**
     * Device-specific cleanup logic.
     * Called during dispose.
     */
    protected abstract void disposeDevice();

    /**
     * Handle device-specific commands that are not common to all devices.
     *
     * @param channelUID The channel UID
     * @param command The command
     */
    protected abstract void handleDeviceCommand(ChannelUID channelUID, Command command);
}
