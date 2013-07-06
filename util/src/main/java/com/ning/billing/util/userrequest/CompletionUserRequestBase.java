/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.util.userrequest;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.ning.billing.bus.api.BusEventWithMetadata;
import com.ning.billing.util.events.AccountChangeInternalEvent;
import com.ning.billing.util.events.AccountCreationInternalEvent;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.events.NullInvoiceInternalEvent;
import com.ning.billing.util.events.PaymentErrorInternalEvent;
import com.ning.billing.util.events.PaymentInfoInternalEvent;

public abstract class CompletionUserRequestBase implements CompletionUserRequest {

    private static final long NANO_TO_MILLI_SEC = (1000L * 1000L);

    private final List<BusInternalEvent> events;

    private final UUID userToken;
    private long timeoutMilliSec;

    private boolean isCompleted;
    private long initialTimeMilliSec;


    public CompletionUserRequestBase(final UUID userToken) {
        this.events = new LinkedList<BusInternalEvent>();
        this.userToken = userToken;
        this.isCompleted = false;
    }

    @Override
    public List<BusInternalEvent> waitForCompletion(final long timeoutMilliSec) throws InterruptedException, TimeoutException {

        this.timeoutMilliSec = timeoutMilliSec;
        initialTimeMilliSec = currentTimeMillis();
        synchronized (this) {
            long remainingTimeMillisSec = getRemainingTimeMillis();
            while (!isCompleted && remainingTimeMillisSec > 0) {
                wait(remainingTimeMillisSec);
                if (isCompleted) {
                    break;
                }
                remainingTimeMillisSec = getRemainingTimeMillis();
            }
            if (!isCompleted) {
                throw new TimeoutException();
            }
        }
        return events;
    }

    @Override
    public void notifyForCompletion() {
        synchronized (this) {
            isCompleted = true;
            notify();
        }
    }

    private long currentTimeMillis() {
        return System.nanoTime() / NANO_TO_MILLI_SEC;
    }

    private long getRemainingTimeMillis() {
        return timeoutMilliSec - (currentTimeMillis() - initialTimeMilliSec);
    }

    @Override
    public void onBusEvent(final BusEventWithMetadata<BusInternalEvent> busEventWithMetadata) {

        final BusInternalEvent curEvent = busEventWithMetadata.getEvent();
        // Check if this is for us..
        if (busEventWithMetadata.getUserToken() == null ||
            !busEventWithMetadata.getUserToken().equals(userToken)) {
            return;
        }
        events.add(curEvent);

        switch (curEvent.getBusEventType()) {
            case ACCOUNT_CREATE:
                onAccountCreation((BusEventWithMetadata<AccountCreationInternalEvent>) curEvent);
                break;
            case ACCOUNT_CHANGE:
                onAccountChange((BusEventWithMetadata<AccountChangeInternalEvent>) curEvent);
                break;
            case SUBSCRIPTION_TRANSITION:
                onSubscriptionTransition((BusEventWithMetadata<EffectiveSubscriptionInternalEvent>) curEvent);
                break;
            case INVOICE_EMPTY:
                onEmptyInvoice((BusEventWithMetadata<NullInvoiceInternalEvent>) curEvent);
                break;
            case INVOICE_CREATION:
                onInvoiceCreation((BusEventWithMetadata<InvoiceCreationInternalEvent>) curEvent);
                break;
            case PAYMENT_INFO:
                onPaymentInfo((BusEventWithMetadata<PaymentInfoInternalEvent>) curEvent);
                break;
            case PAYMENT_ERROR:
                onPaymentError((BusEventWithMetadata<PaymentErrorInternalEvent>) curEvent);
                break;
            default:
                throw new RuntimeException("Unexpected event type " + curEvent.getBusEventType());
        }
    }

    /*
     *
     * Default no-op implementation so as to not have to implement all callbacks
     */
    @Override
    public void onAccountCreation(final BusEventWithMetadata<AccountCreationInternalEvent> curEvent) {
    }

    @Override
    public void onAccountChange(final BusEventWithMetadata<AccountChangeInternalEvent> curEvent) {
    }

    @Override
    public void onSubscriptionTransition(final BusEventWithMetadata<EffectiveSubscriptionInternalEvent> curEventEffective) {
    }

    @Override
    public void onEmptyInvoice(final BusEventWithMetadata<NullInvoiceInternalEvent> curEvent) {
    }

    @Override
    public void onInvoiceCreation(final BusEventWithMetadata<InvoiceCreationInternalEvent> curEvent) {
    }

    @Override
    public void onPaymentInfo(final BusEventWithMetadata<PaymentInfoInternalEvent> curEvent) {
    }

    @Override
    public void onPaymentError(final BusEventWithMetadata<PaymentErrorInternalEvent> curEvent) {
    }
}
