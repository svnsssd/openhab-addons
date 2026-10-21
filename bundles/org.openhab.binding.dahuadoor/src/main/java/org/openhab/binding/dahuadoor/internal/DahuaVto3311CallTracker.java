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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/** Tracks pending VTO3311 button indices until their matching Invite event arrives. */
@NonNullByDefault
final class DahuaVto3311CallTracker {

    static final long CALL_INDEX_TTL_MS = 10_000;

    private final Map<String, PendingCall> pendingCalls = new ConcurrentHashMap<>();

    boolean remember(String callId, int buttonIndex, long now) {
        if (callId.isBlank() || buttonIndex < 1 || buttonIndex > 4) {
            return false;
        }
        removeExpired(now);
        pendingCalls.put(callId, new PendingCall(buttonIndex, now));
        return true;
    }

    @Nullable
    Integer consume(String callId, long now) {
        removeExpired(now);
        PendingCall pendingCall = pendingCalls.remove(callId);
        return pendingCall == null ? null : pendingCall.buttonIndex();
    }

    private void removeExpired(long now) {
        pendingCalls.entrySet().removeIf(entry -> now - entry.getValue().recordedAt() >= CALL_INDEX_TTL_MS);
    }

    private record PendingCall(int buttonIndex, long recordedAt) {
    }
}
