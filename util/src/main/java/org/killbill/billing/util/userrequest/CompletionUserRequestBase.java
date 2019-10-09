/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.util.userrequest;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.killbill.billing.events.AccountChangeInternalEvent;
import org.killbill.billing.events.AccountCreationInternalEvent;
import org.killbill.billing.events.BlockingTransitionInternalEvent;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.InvoicePaymentErrorInternalEvent;
import org.killbill.billing.events.InvoicePaymentInfoInternalEvent;
import org.killbill.billing.events.NullInvoiceInternalEvent;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.events.PaymentPluginErrorInternalEvent;

public class CompletionUserRequestBase implements CompletionUserRequest {

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
    public void onBusEvent(final BusInternalEvent curEvent) {

        // Check if this is for us..
        if (curEvent.getUserToken() == null ||
            !curEvent.getUserToken().equals(userToken)) {
            return;
        }
        events.add(curEvent);

        switch (curEvent.getBusEventType()) {
            case ACCOUNT_CREATE:
                onAccountCreation((AccountCreationInternalEvent) curEvent);
                break;
            case ACCOUNT_CHANGE:
                onAccountChange((AccountChangeInternalEvent) curEvent);
                break;
            case BLOCKING_STATE:
                onBlockingState((BlockingTransitionInternalEvent) curEvent);
                break;
            case SUBSCRIPTION_TRANSITION:
                // We only dispatch the event for the effective date and not the requested date since we have both
                // for subscription events.
                if (curEvent instanceof EffectiveSubscriptionInternalEvent) {
                    onSubscriptionBaseTransition((EffectiveSubscriptionInternalEvent) curEvent);
                }
                break;
            case INVOICE_EMPTY:
                onEmptyInvoice((NullInvoiceInternalEvent) curEvent);
                break;
            case INVOICE_CREATION:
                onInvoiceCreation((InvoiceCreationInternalEvent) curEvent);
                break;
            case PAYMENT_INFO:
                onPaymentInfo((PaymentInfoInternalEvent) curEvent);
                break;
            case PAYMENT_ERROR:
                onPaymentError((PaymentErrorInternalEvent) curEvent);
                break;
            case PAYMENT_PLUGIN_ERROR:
                onPaymentPluginError((PaymentPluginErrorInternalEvent) curEvent);
                break;
            case INVOICE_PAYMENT_INFO:
                onInvoicePaymentInfo((InvoicePaymentInfoInternalEvent) curEvent);
                break;
            case INVOICE_PAYMENT_ERROR:
                onInvoicePaymentError((InvoicePaymentErrorInternalEvent) curEvent);
                break;
            default:
                // Ignore unexpected events: these could come from custom control plugins for instance (https://github.com/killbill/killbill/issues/1211)
                break;
        }
    }

    @Override
    public void onAccountCreation(final AccountCreationInternalEvent curEvent) {
    }

    @Override
    public void onAccountChange(final AccountChangeInternalEvent curEvent) {
    }

    @Override
    public void onSubscriptionBaseTransition(final EffectiveSubscriptionInternalEvent curEventEffective) {
    }

    @Override
    public void onBlockingState(final BlockingTransitionInternalEvent curEvent) {

    }

    @Override
    public void onInvoiceCreation(final InvoiceCreationInternalEvent curEvent) {
    }

    @Override
    public void onEmptyInvoice(final NullInvoiceInternalEvent curEvent) {
    }

    @Override
    public void onPaymentInfo(final PaymentInfoInternalEvent curEvent) {
    }

    @Override
    public void onPaymentError(final PaymentErrorInternalEvent curEvent) {
    }

    @Override
    public void onPaymentPluginError(final PaymentPluginErrorInternalEvent curEvent) {
    }

    @Override
    public void onInvoicePaymentInfo(final InvoicePaymentInfoInternalEvent curEvent) {
    }

    @Override
    public void onInvoicePaymentError(final InvoicePaymentErrorInternalEvent curEvent) {
    }
}
