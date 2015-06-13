/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.payment.core.sm;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.Operation;
import org.killbill.automaton.State;
import org.killbill.automaton.StateMachine;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.payment.glue.PaymentModule;

public class PaymentControlStateMachineHelper {

    /**
     * Those need to match RetryStates.xml
     */
    private static final String PAYMENT_CONTROL_STATE_MACHINE_NAME = "PAYMENT_RETRY";
    private final String RETRY_OPERATION_NAME = "OP_RETRY";
    private static final String INIT_STATE_NAME = "INIT";
    private static final String RETRIED_STATE_NAME = "RETRIED";

    private final StateMachineConfig stateMachineConfig;
    private final StateMachine stateMachine;
    private final Operation operation;
    private final State initialState;
    private final State retriedState;

    @Inject
    public PaymentControlStateMachineHelper(@Named(PaymentModule.STATE_MACHINE_RETRY) final StateMachineConfig retryStateMachineConfig) throws MissingEntryException {
        this.stateMachineConfig = retryStateMachineConfig;
        this.stateMachine = retryStateMachineConfig.getStateMachine(PAYMENT_CONTROL_STATE_MACHINE_NAME);
        this.operation = stateMachine.getOperation(RETRY_OPERATION_NAME);
        this.initialState = stateMachine.getState(INIT_STATE_NAME);
        this.retriedState = stateMachine.getState(RETRIED_STATE_NAME);
    }

    public State getState(final String stateName) throws MissingEntryException {
        return stateMachine.getState(stateName);
    }

    public Operation getOperation() {
        return operation;
    }

    public State getInitialState() {
        return initialState;
    }

    public State getRetriedState() {
        return retriedState;
    }
}
