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
 * The {@link DahuaVto2202Handler} handles Dahua VTO2202F devices with a single doorbell button.
 *
 * @author Sven Schad - Initial contribution
 */
@NonNullByDefault
public class DahuaVto2202Handler extends DahuaBaseThingHandler {

    public DahuaVto2202Handler(Thing thing) {
        super(thing);
    }

    @Override
    public void onButtonPressed(int buttonNumber) {
        // VTO2202 has only one button, always treat as button press
        logger.debug("Button pressed on VTO2202");

        // Trigger bell button channel
        Channel channel = getThing().getChannel(CHANNEL_BELL_BUTTON);
        if (channel == null) {
            logger.warn("Bell button channel not found");
            return;
        }
        triggerChannel(channel.getUID(), "PRESSED");

        // Request and update image
        byte[] buffer = requestImage();
        if (buffer != null) {
            updateChannelImage(CHANNEL_DOOR_IMAGE, buffer);
            saveSnapshot(buffer, "");
        }
    }

    @Override
    protected void initializeDevice() {
        logger.debug("Initializing VTO2202 device");
    }

    @Override
    protected void disposeDevice() {
        logger.debug("Disposing VTO2202 device");
    }

    @Override
    protected void handleDeviceCommand(ChannelUID channelUID, Command command) {
        // VTO2202 has no device-specific commands beyond door control
    }
}
