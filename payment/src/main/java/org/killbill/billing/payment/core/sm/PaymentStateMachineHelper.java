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

import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.Operation;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.StateMachine;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.automaton.Transition;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.glue.PaymentModule;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * This class needs to know about the payment state machine xml file. All the knowledge about the xml file is encapsulated here.
 */
public class PaymentStateMachineHelper {

    private static final String BIG_BANG_STATE_MACHINE_NAME = "BIG_BANG";
    private static final String AUTHORIZE_STATE_MACHINE_NAME = "AUTHORIZE";
    private static final String CAPTURE_STATE_MACHINE_NAME = "CAPTURE";
    private static final String PURCHASE_STATE_MACHINE_NAME = "PURCHASE";
    private static final String REFUND_STATE_MACHINE_NAME = "REFUND";
    private static final String CREDIT_STATE_MACHINE_NAME = "CREDIT";
    private static final String VOID_STATE_MACHINE_NAME = "VOID";
    private static final String CHARGEBACK_STATE_MACHINE_NAME = "CHARGEBACK";

    private static final String BIG_BANG_INIT = "BIG_BANG_INIT";

    private static final String AUTHORIZE_SUCCESS = "AUTH_SUCCESS";
    private static final String CAPTURE_SUCCESS = "CAPTURE_SUCCESS";
    private static final String PURCHASE_SUCCESS = "PURCHASE_SUCCESS";
    private static final String REFUND_SUCCESS = "REFUND_SUCCESS";
    private static final String CREDIT_SUCCESS = "CREDIT_SUCCESS";
    private static final String VOID_SUCCESS = "VOID_SUCCESS";
    private static final String CHARGEBACK_SUCCESS = "CHARGEBACK_SUCCESS";

    private static final String AUTHORIZE_PENDING = "AUTH_PENDING";
    private static final String PURCHASE_PENDING = "PURCHASE_PENDING";
    private static final String REFUND_PENDING = "REFUND_PENDING";
    private static final String CREDIT_PENDING = "CREDIT_PENDING";

    private static final String AUTHORIZE_FAILED = "AUTH_FAILED";
    private static final String CAPTURE_FAILED = "CAPTURE_FAILED";
    private static final String PURCHASE_FAILED = "PURCHASE_FAILED";
    private static final String REFUND_FAILED = "REFUND_FAILED";
    private static final String CREDIT_FAILED = "CREDIT_FAILED";
    private static final String VOID_FAILED = "VOID_FAILED";
    private static final String CHARGEBACK_FAILED = "CHARGEBACK_FAILED";

    private static final String AUTH_ERRORED = "AUTH_ERRORED";
    private static final String CAPTURE_ERRORED = "CAPTURE_ERRORED";
    private static final String PURCHASE_ERRORED = "PURCHASE_ERRORED";
    private static final String REFUND_ERRORED = "REFUND_ERRORED";
    private static final String CREDIT_ERRORED = "CREDIT_ERRORED";
    private static final String VOID_ERRORED = "VOID_ERRORED";
    private static final String CHARGEBACK_ERRORED = "CHARGEBACK_ERRORED";
    private final StateMachineConfig stateMachineConfig;
    private final String[] errorStateNames = {AUTH_ERRORED, CAPTURE_ERRORED, PURCHASE_ERRORED, REFUND_ERRORED, CREDIT_ERRORED, VOID_ERRORED, CHARGEBACK_ERRORED};

    @Inject
    public PaymentStateMachineHelper(@javax.inject.Named(PaymentModule.STATE_MACHINE_PAYMENT) final StateMachineConfig stateMachineConfig) {
        this.stateMachineConfig = stateMachineConfig;
    }

    public State getState(final String stateName) throws MissingEntryException {
        final StateMachine stateMachine = stateMachineConfig.getStateMachineForState(stateName);
        return stateMachine.getState(stateName);
    }

    public String getInitStateNameForTransaction() {
        return BIG_BANG_INIT;
    }

    public String getSuccessfulStateForTransaction(final TransactionType transactionType) {
        switch (transactionType) {
            case AUTHORIZE:
                return AUTHORIZE_SUCCESS;
            case CAPTURE:
                return CAPTURE_SUCCESS;
            case PURCHASE:
                return PURCHASE_SUCCESS;
            case REFUND:
                return REFUND_SUCCESS;
            case CREDIT:
                return CREDIT_SUCCESS;
            case VOID:
                return VOID_SUCCESS;
            case CHARGEBACK:
                return CHARGEBACK_SUCCESS;
            default:
                throw new IllegalStateException("Unsupported transaction type " + transactionType);
        }
    }

    public String getPendingStateForTransaction(final TransactionType transactionType) {
        switch (transactionType) {
            case AUTHORIZE:
                return AUTHORIZE_PENDING;
            case PURCHASE:
                return PURCHASE_PENDING;
            case REFUND:
                return REFUND_PENDING;
            case CREDIT:
                return CREDIT_PENDING;
            default:
                throw new IllegalStateException("No PENDING state for transaction type " + transactionType);
        }
    }

    public String getErroredStateForTransaction(final TransactionType transactionType) {
        switch (transactionType) {
            case AUTHORIZE:
                return AUTH_ERRORED;
            case CAPTURE:
                return CAPTURE_ERRORED;
            case PURCHASE:
                return PURCHASE_ERRORED;
            case REFUND:
                return REFUND_ERRORED;
            case CREDIT:
                return CREDIT_ERRORED;
            case VOID:
                return VOID_ERRORED;
            case CHARGEBACK:
                return CHARGEBACK_ERRORED;
            default:
                throw new IllegalStateException("Unsupported transaction type " + transactionType);
        }
    }

    public String getFailureStateForTransaction(final TransactionType transactionType) {
        switch (transactionType) {
            case AUTHORIZE:
                return AUTHORIZE_FAILED;
            case CAPTURE:
                return CAPTURE_FAILED;
            case PURCHASE:
                return PURCHASE_FAILED;
            case REFUND:
                return REFUND_FAILED;
            case CREDIT:
                return CREDIT_FAILED;
            case VOID:
                return VOID_FAILED;
            case CHARGEBACK:
                return CHARGEBACK_FAILED;
            default:
                throw new IllegalStateException("Unsupported transaction type " + transactionType);
        }
    }

    public StateMachine getStateMachineForStateName(final String stateName) throws MissingEntryException {
        return stateMachineConfig.getStateMachineForState(stateName);
    }

    public Operation getOperationForTransaction(final TransactionType transactionType) throws MissingEntryException {
        final StateMachine stateMachine = getStateMachineForTransaction(transactionType);
        // Only one operation defined, this is the current PaymentStates.xml model
        return stateMachine.getOperations()[0];
    }

    public StateMachine getStateMachineForTransaction(final TransactionType transactionType) throws MissingEntryException {
        switch (transactionType) {
            case AUTHORIZE:
                return stateMachineConfig.getStateMachine(AUTHORIZE_STATE_MACHINE_NAME);
            case CAPTURE:
                return stateMachineConfig.getStateMachine(CAPTURE_STATE_MACHINE_NAME);
            case PURCHASE:
                return stateMachineConfig.getStateMachine(PURCHASE_STATE_MACHINE_NAME);
            case REFUND:
                return stateMachineConfig.getStateMachine(REFUND_STATE_MACHINE_NAME);
            case CREDIT:
                return stateMachineConfig.getStateMachine(CREDIT_STATE_MACHINE_NAME);
            case VOID:
                return stateMachineConfig.getStateMachine(VOID_STATE_MACHINE_NAME);
            case CHARGEBACK:
                return stateMachineConfig.getStateMachine(CHARGEBACK_STATE_MACHINE_NAME);
            default:
                throw new IllegalStateException("Unsupported transaction type " + transactionType + " for null payment id");
        }
    }

    // A better way would be to change the xml to add attributes to the state (e.g isTerminal, isSuccess, isInit,...)
    public boolean isSuccessState(final String stateName) {
        return stateName.endsWith("SUCCESS");
    }

    public final State fetchNextState(final String prevStateName, final boolean isSuccess) throws MissingEntryException {
        final StateMachine stateMachine = getStateMachineForStateName(prevStateName);
        final Transition transition = Iterables.tryFind(ImmutableList.copyOf(stateMachine.getTransitions()), new Predicate<Transition>() {
            @Override
            public boolean apply(final Transition input) {
                // This works because there is only one operation defined for a given state machine, which is our model for PaymentStates.xml
                return input.getInitialState().getName().equals(prevStateName) &&
                       input.getOperationResult().equals(isSuccess ? OperationResult.SUCCESS : OperationResult.FAILURE);
            }
        }).orNull();
        return transition != null ? transition.getFinalState() : null;
    }

    public String[] getErroredStateNames() {
        return errorStateNames;
    }

}
