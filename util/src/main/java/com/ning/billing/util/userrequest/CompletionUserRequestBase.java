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

import com.ning.billing.account.api.AccountChangeNotification;
import com.ning.billing.account.api.AccountCreationNotification;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.invoice.api.EmptyInvoiceNotification;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;
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
        synchronized(this) {
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
        synchronized(this) {
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
    public void onBusEvent(BusEvent curEvent) {
        // Check if this is for us..
        if (curEvent.getUserToken() == null ||
                ! curEvent.getUserToken().equals(userToken)) {
            return;
        }
        
        events.add(curEvent);
        
        switch(curEvent.getBusEventType()) {
        case ACCOUNT_CREATE:
            onAccountCreation((AccountCreationNotification) curEvent);
            break;
        case ACCOUNT_CHANGE:
            onAccountChange((AccountChangeNotification) curEvent);
            break;
        case SUBSCRIPTION_TRANSITION:
            onSubscriptionTransition((SubscriptionTransition) curEvent);
            break;
        case INVOICE_EMPTY:
            onEmptyInvoice((EmptyInvoiceNotification) curEvent);
            break;
        case INVOICE_CREATION:
            onInvoiceCreation((InvoiceCreationNotification) curEvent);
            break;
        case PAYMENT_INFO:
            onPaymentInfo((PaymentInfo) curEvent);
            break;
        case PAYMENT_ERROR:
            onPaymentError((PaymentError) curEvent);
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
    public void onAccountCreation(final AccountCreationNotification curEvent) {
    }

    @Override
    public void onAccountChange(final AccountChangeNotification curEvent) {
    }

    @Override
    public void onSubscriptionTransition(final SubscriptionTransition curEvent) {
    }

    @Override
    public void onEmptyInvoice(final EmptyInvoiceNotification curEvent) {
    }
    
    @Override
    public void onInvoiceCreation(final InvoiceCreationNotification curEvent) {
    }

    @Override
    public void onPaymentInfo(final PaymentInfo curEvent) {
    }

    @Override
    public void onPaymentError(final PaymentError curEvent) {
    }
}
