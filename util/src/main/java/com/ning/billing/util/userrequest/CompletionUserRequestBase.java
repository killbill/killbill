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

import com.ning.billing.account.api.AccountChangeEvent;
import com.ning.billing.account.api.AccountCreationEvent;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.invoice.api.EmptyInvoiceEvent;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.util.bus.BusEvent;

public abstract class CompletionUserRequestBase implements CompletionUserRequest {

    private static final long NANO_TO_MILLI_SEC = (1000L * 1000L);

    private final List<BusEvent> events;

    private final UUID userToken;
    private long timeoutMilliSec;

    private boolean isCompleted;
    private long initialTimeMilliSec;


    public CompletionUserRequestBase(final UUID userToken) {
        this.events = new LinkedList<BusEvent>();
        this.userToken = userToken;
        this.isCompleted = false;
    }

    @Override
    public List<BusEvent> waitForCompletion(final long timeoutMilliSec) throws InterruptedException, TimeoutException {

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
    public void onBusEvent(final BusEvent curEvent) {
        // Check if this is for us..
        if (curEvent.getUserToken() == null ||
                !curEvent.getUserToken().equals(userToken)) {
            return;
        }

        events.add(curEvent);

        switch (curEvent.getBusEventType()) {
            case ACCOUNT_CREATE:
                onAccountCreation((AccountCreationEvent) curEvent);
                break;
            case ACCOUNT_CHANGE:
                onAccountChange((AccountChangeEvent) curEvent);
                break;
            case SUBSCRIPTION_TRANSITION:
                onSubscriptionTransition((EffectiveSubscriptionEvent) curEvent);
                break;
            case INVOICE_EMPTY:
                onEmptyInvoice((EmptyInvoiceEvent) curEvent);
                break;
            case INVOICE_CREATION:
                onInvoiceCreation((InvoiceCreationEvent) curEvent);
                break;
            case PAYMENT_INFO:
                onPaymentInfo((PaymentInfoEvent) curEvent);
                break;
            case PAYMENT_ERROR:
                onPaymentError((PaymentErrorEvent) curEvent);
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
    public void onAccountCreation(final AccountCreationEvent curEvent) {
    }

    @Override
    public void onAccountChange(final AccountChangeEvent curEvent) {
    }

    @Override
    public void onSubscriptionTransition(final EffectiveSubscriptionEvent curEventEffective) {
    }

    @Override
    public void onEmptyInvoice(final EmptyInvoiceEvent curEvent) {
    }

    @Override
    public void onInvoiceCreation(final InvoiceCreationEvent curEvent) {
    }

    @Override
    public void onPaymentInfo(final PaymentInfoEvent curEvent) {
    }

    @Override
    public void onPaymentError(final PaymentErrorEvent curEvent) {
    }
}
