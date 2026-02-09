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
package org.openhab.binding.enocean.internal.statemachine;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Predefined transition configurations for different device types.
 * <p>
 * Device-specific configurations should be added when implementing specific device handlers.
 *
 * @author Sven Schad - Initial contribution
 */
@NonNullByDefault
public enum STMTransitionConfiguration {
    ; // Placeholder - device-specific configurations will be added in device implementations

    private List<STMTransition> transitions;

    STMTransitionConfiguration(ArrayList<STMTransition> transitions) {
        this.transitions = transitions;
    }

    public List<STMTransition> getTransitions() {
        return transitions;
    }
}
