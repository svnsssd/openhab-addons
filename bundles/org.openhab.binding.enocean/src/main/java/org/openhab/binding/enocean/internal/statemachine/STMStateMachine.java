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

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic state machine for managing multi-stage device movements.
 * <p>
 * This state machine is decoupled from specific device handlers and uses
 * a callback mechanism to notify state changes. The caller is responsible
 * for persisting state and updating channels.
 *
 * @author Sven Schad - Initial contribution
 */
@NonNullByDefault
public class STMStateMachine {

    private final List<STMTransition> transitions;
    private final HashMap<STMAction, Runnable> callbackActions;
    private final ScheduledExecutorService scheduler;
    private final @Nullable Consumer<STMState> stateChangeCallback;

    private STMState state;
    private STMState prevState;

    protected @Nullable ScheduledFuture<?> responseFuture = null;

    protected Logger logger = LoggerFactory.getLogger(STMStateMachine.class);

    /**
     * Creates a new state machine instance.
     *
     * @param config the transition configuration to use
     * @param initState the initial state
     * @param scheduler the scheduler for delayed operations
     * @param stateChangeCallback optional callback invoked on state changes
     * @return new state machine instance
     */
    public static STMStateMachine build(STMTransitionConfiguration config, STMState initState,
            ScheduledExecutorService scheduler, @Nullable Consumer<STMState> stateChangeCallback) {
        return new STMStateMachine(config, initState, scheduler, stateChangeCallback);
    }

    private STMStateMachine(STMTransitionConfiguration config, STMState initState, ScheduledExecutorService scheduler,
            @Nullable Consumer<STMState> stateChangeCallback) {
        this.callbackActions = new HashMap<>();
        this.state = initState;
        this.prevState = initState;
        this.transitions = config.getTransitions();
        this.scheduler = scheduler;
        this.stateChangeCallback = stateChangeCallback;
    }

    /**
     * Registers a callback to be executed when a specific action is applied.
     *
     * @param action the action that triggers the callback
     * @param callback the callback to execute
     * @return this state machine for fluent configuration
     */
    public synchronized STMStateMachine register(STMAction action, Runnable callback) {
        this.callbackActions.put(action, callback);
        return this;
    }

    /**
     * Gets the current state.
     *
     * @return current state
     */
    public synchronized STMState getState() {
        return state;
    }

    /**
     * Gets the previous state before the last transition.
     *
     * @return previous state
     */
    public synchronized STMState getPrevState() {
        return prevState;
    }

    /**
     * Restores a previously persisted state.
     * <p>
     * This method is intended to be called during handler initialization
     * to restore state from Thing properties. No callback is triggered.
     *
     * @param restoredState the state to restore
     */
    public synchronized void restoreState(STMState restoredState) {
        this.prevState = this.state;
        this.state = restoredState;
        logger.debug("STM: State restored to {}", restoredState);
    }

    /**
     * Applies an action to trigger a state transition.
     * <p>
     * If a matching transition is found, the state changes and registered
     * callbacks are invoked.
     *
     * @param action the action to apply
     * @return this state machine for fluent usage
     */
    @SuppressWarnings("null")
    public synchronized STMStateMachine apply(STMAction action) {
        for (STMTransition transition : transitions) {
            boolean currentStateMatches = transition.from.equals(state);
            boolean conditionsMatch = transition.action.equals(action);

            if (currentStateMatches && conditionsMatch) {
                logger.debug("STM: State change from {} to {} by action {}, prevState {}", state, transition.to, action,
                        prevState);
                prevState = state;
                state = transition.to;

                // Execute action-specific callback if registered
                if (callbackActions.containsKey(action)) {
                    callbackActions.get(action).run();
                }

                // Notify state change via callback
                if (stateChangeCallback != null) {
                    stateChangeCallback.accept(state);
                }

                break;
            }
        }

        return this;
    }

    /**
     * Schedules a runnable to be executed after a delay.
     * <p>
     * Useful for scheduling follow-up commands after movement completion.
     *
     * @param runnable the task to execute
     * @param delayMs delay in milliseconds
     */
    @SuppressWarnings("null")
    public void scheduleDelayed(Runnable runnable, long delayMs) {
        if (responseFuture == null || responseFuture.isDone()) {
            this.responseFuture = scheduler.schedule(runnable, delayMs, TimeUnit.MILLISECONDS);
        }
    }
}
