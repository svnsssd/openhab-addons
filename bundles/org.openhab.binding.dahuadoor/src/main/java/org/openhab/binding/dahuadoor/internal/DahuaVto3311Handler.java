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
package org.openhab.binding.dahuadoor.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.dahuadoor.internal.media.PlayStreamServlet;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.Thing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Handler for the four-button Dahua VTO3311 door station.
 *
 * @author Sven Schad - Initial contribution
 */
@NonNullByDefault
public class DahuaVto3311Handler extends DahuaDoorBaseHandler {

    private final DahuaVto3311CallTracker callTracker = new DahuaVto3311CallTracker();

    public DahuaVto3311Handler(Thing thing, PlayStreamServlet playStreamServlet) {
        super(thing, playStreamServlet);
    }

    @Override
    protected void handleCallNoAnswered(JsonObject eventList, JsonObject eventData) {
        super.handleCallNoAnswered(eventList, eventData);

        JsonElement actionElement = eventList.get("Action");
        if (actionElement == null || !actionElement.isJsonPrimitive() || !"Start".equals(actionElement.getAsString())) {
            return;
        }

        String callId = getCallId(eventData);
        JsonElement indexElement = eventList.get("Index");
        if (callId == null || indexElement == null || !indexElement.isJsonPrimitive()) {
            logger.warn("Ignoring VTO3311 CallNoAnswered event without a CallID or button index");
            return;
        }

        int buttonIndex;
        try {
            buttonIndex = indexElement.getAsInt();
        } catch (NumberFormatException e) {
            logger.warn("Ignoring VTO3311 CallNoAnswered event with invalid button index {}", indexElement);
            return;
        }

        if (!callTracker.remember(callId, buttonIndex, System.currentTimeMillis())) {
            logger.warn("Ignoring VTO3311 CallNoAnswered event with invalid CallID or button index {}", buttonIndex);
        } else {
            logger.debug("Stored VTO3311 button index {} for CallID {}", buttonIndex, callId);
        }
    }

    @Override
    protected void handleInvite(JsonObject eventList, JsonObject eventData) {
        String callId = getCallId(eventData);
        if (callId == null) {
            logger.warn("Ignoring VTO3311 Invite event without a CallID");
            return;
        }

        Integer buttonIndex = callTracker.consume(callId, System.currentTimeMillis());
        if (buttonIndex == null) {
            logger.warn("Ignoring VTO3311 Invite for CallID {} without a matching CallNoAnswered button index", callId);
            return;
        }

        logger.debug("Resolved VTO3311 Invite for CallID {} to button {}", callId, buttonIndex);
        handleResolvedDoorbellEvent("DHIP", buttonIndex);
    }

    @Override
    protected void restoreLastSnapshots() {
        updateImageChannel(DahuaDoorBindingConstants.CHANNEL_DOOR_IMAGE, readLatestSnapshot());
    }

    @Override
    protected String getDoorImageChannelForLock(int lockNumber) {
        return DahuaDoorBindingConstants.CHANNEL_DOOR_IMAGE;
    }

    @Override
    protected String getDoorbellSnapshotSuffixForLock(int lockNumber) {
        return "";
    }

    @Override
    protected void onButtonPressed(int lockNumber) {
        String channelId = getBellButtonChannelId(lockNumber);
        if (channelId == null) {
            logger.warn("Ignoring unsupported VTO3311 button index {}", lockNumber);
            return;
        }

        logger.debug("Button {} pressed on VTO3311", lockNumber);
        Channel bellChannel = getThing().getChannel(channelId);
        if (bellChannel == null) {
            logger.warn("Bell button channel '{}' not found", channelId);
            return;
        }
        triggerChannel(bellChannel.getUID(), "PRESSED");

        if (isDhipSnapshotMode()) {
            logger.debug("snapshotMode=dhip: waiting for NewFile snapshot event for button {}", lockNumber);
            return;
        }

        scheduler.submit(() -> requestApiSnapshotForChannel(DahuaDoorBindingConstants.CHANNEL_DOOR_IMAGE,
                "VTO3311 button " + lockNumber));
    }

    static @Nullable String getBellButtonChannelId(int buttonIndex) {
        return switch (buttonIndex) {
            case 1 -> DahuaDoorBindingConstants.CHANNEL_BELL_BUTTON_1;
            case 2 -> DahuaDoorBindingConstants.CHANNEL_BELL_BUTTON_2;
            case 3 -> DahuaDoorBindingConstants.CHANNEL_BELL_BUTTON_3;
            case 4 -> DahuaDoorBindingConstants.CHANNEL_BELL_BUTTON_4;
            default -> null;
        };
    }

    private @Nullable String getCallId(JsonObject eventData) {
        JsonElement callIdElement = eventData.get("CallID");
        if (callIdElement == null || !callIdElement.isJsonPrimitive()) {
            return null;
        }
        String callId = callIdElement.getAsString();
        return callId.isBlank() ? null : callId;
    }
}
