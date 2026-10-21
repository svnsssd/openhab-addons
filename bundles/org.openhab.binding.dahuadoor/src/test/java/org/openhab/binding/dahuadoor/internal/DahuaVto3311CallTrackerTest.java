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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

@NonNullByDefault
class DahuaVto3311CallTrackerTest {

    @Test
    void consumesIndexForMatchingCallIdOnlyOnce() {
        DahuaVto3311CallTracker tracker = new DahuaVto3311CallTracker();

        assertTrue(tracker.remember("call-1", 3, 1_000));

        assertEquals(3, tracker.consume("call-1", 1_100));
        assertNull(tracker.consume("call-1", 1_101));
        assertNull(tracker.consume("call-2", 1_102));
    }

    @Test
    void keepsIndicesForIndependentCallIds() {
        DahuaVto3311CallTracker tracker = new DahuaVto3311CallTracker();

        assertTrue(tracker.remember("call-1", 1, 1_000));
        assertTrue(tracker.remember("call-2", 4, 1_001));

        assertEquals(4, tracker.consume("call-2", 1_100));
        assertEquals(1, tracker.consume("call-1", 1_101));
    }

    @Test
    void doesNotReturnExpiredIndex() {
        DahuaVto3311CallTracker tracker = new DahuaVto3311CallTracker();

        assertTrue(tracker.remember("call-1", 2, 1_000));

        assertNull(tracker.consume("call-1", 1_000 + DahuaVto3311CallTracker.CALL_INDEX_TTL_MS));
    }

    @Test
    void rejectsMissingCallIdAndOutOfRangeButtonIndices() {
        DahuaVto3311CallTracker tracker = new DahuaVto3311CallTracker();

        assertFalse(tracker.remember("", 1, 1_000));
        assertFalse(tracker.remember("call-1", 0, 1_000));
        assertFalse(tracker.remember("call-2", 5, 1_000));
        assertNull(tracker.consume("call-1", 1_001));
    }

    @Test
    void mapsPhysicalButtonIndicesToTheirTriggerChannels() {
        assertEquals(DahuaDoorBindingConstants.CHANNEL_BELL_BUTTON_1, DahuaVto3311Handler.getBellButtonChannelId(1));
        assertEquals(DahuaDoorBindingConstants.CHANNEL_BELL_BUTTON_2, DahuaVto3311Handler.getBellButtonChannelId(2));
        assertEquals(DahuaDoorBindingConstants.CHANNEL_BELL_BUTTON_3, DahuaVto3311Handler.getBellButtonChannelId(3));
        assertEquals(DahuaDoorBindingConstants.CHANNEL_BELL_BUTTON_4, DahuaVto3311Handler.getBellButtonChannelId(4));
        assertNull(DahuaVto3311Handler.getBellButtonChannelId(0));
        assertNull(DahuaVto3311Handler.getBellButtonChannelId(5));
    }
}
