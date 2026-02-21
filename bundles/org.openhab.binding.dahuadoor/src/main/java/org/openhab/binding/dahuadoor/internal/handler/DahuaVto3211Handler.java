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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;

/**
 * The {@link DahuaVto3211Handler} handles Dahua VTO3211 devices with two doorbell buttons.
 *
 * @author Sven Schad - Initial contribution
 */
@NonNullByDefault
public class DahuaVto3211Handler extends DahuaBaseThingHandler {

    public DahuaVto3211Handler(Thing thing) {
        super(thing);
    }

    @Override
    public void onButtonPressed(int buttonNumber) {
        logger.debug("Button {} pressed on VTO3211", buttonNumber);

        // Route to correct channel based on button number
        String bellChannelId;
        String imageChannelId;
        String suffix;

        if (buttonNumber == 1) {
            bellChannelId = CHANNEL_BELL_BUTTON_1;
            imageChannelId = CHANNEL_DOOR_IMAGE_1;
            suffix = "-1";
        } else if (buttonNumber == 2) {
            bellChannelId = CHANNEL_BELL_BUTTON_2;
            imageChannelId = CHANNEL_DOOR_IMAGE_2;
            suffix = "-2";
        } else {
            logger.warn("Unknown button number {} for VTO3211", buttonNumber);
            return;
        }

        // Trigger the appropriate bell button channel
        Channel channel = getThing().getChannel(bellChannelId);
        if (channel == null) {
            logger.warn("Bell button channel {} not found", bellChannelId);
            return;
        }
        triggerChannel(channel.getUID(), "PRESSED");

        // Request and update image for this button
        byte[] buffer = requestImage();
        if (buffer != null) {
            updateChannelImage(imageChannelId, buffer);
            saveSnapshot(buffer, suffix);
        }
    }

    @Override
    protected void initializeDevice() {
        logger.debug("Initializing VTO3211 device");
    }

    @Override
    protected void disposeDevice() {
        logger.debug("Disposing VTO3211 device");
    }

    @Override
    protected void handleDeviceCommand(ChannelUID channelUID, Command command) {
        // VTO3211 has no device-specific commands beyond door control
    }
}
