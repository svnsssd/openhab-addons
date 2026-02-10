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
package org.openhab.binding.enocean.internal.eep;

import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.enocean.internal.statemachine.STMAction;
import org.openhab.binding.enocean.internal.statemachine.STMState;
import org.openhab.binding.enocean.internal.statemachine.STMTransitionConfiguration;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.builder.ThingBuilder;

/**
 * Interface for EEPs that require a state machine for operation.
 * <p>
 * EEPs implementing this interface provide their state machine configuration
 * and channel initialization logic, allowing the handler to remain generic.
 *
 * @author Sven Schad - Initial contribution
 */
@NonNullByDefault
public interface STMCapable {

    /**
     * Gets the transition configuration for the state machine.
     * The implementation may read device-specific configuration from the Thing.
     *
     * @param thing the Thing to read configuration from
     * @return the transition configuration, or null if STM should not be used (e.g., legacy mode)
     */
    @Nullable
    STMTransitionConfiguration getTransitionConfiguration(Thing thing);

    /**
     * Gets the initial state for the state machine.
     *
     * @return the initial state
     */
    STMState getInitialState();

    /**
     * Gets the actions that require callback registration.
     * These actions will trigger processStoredCommand when completed.
     *
     * @param thing the Thing to read configuration from
     * @return set of actions requiring callbacks
     */
    Set<STMAction> getRequiredCallbackActions(Thing thing);

    /**
     * Initializes channels based on the device configuration.
     * This may remove channels that are not needed for the current mode.
     *
     * @param thing the Thing to read configuration from
     * @param thingBuilder the builder to modify channels
     * @param updateThing consumer to apply the modified Thing
     */
    void initializeChannels(Thing thing, ThingBuilder thingBuilder, Consumer<Thing> updateThing);
}
