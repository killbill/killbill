/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import java.util.List;
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

public interface CompletionUserRequestWaiter {

    public List<BusInternalEvent> waitForCompletion(final long timeoutMilliSec) throws InterruptedException, TimeoutException;

    public void onAccountCreation(final AccountCreationInternalEvent curEvent);

    public void onAccountChange(final AccountChangeInternalEvent curEvent);

    public void onSubscriptionBaseTransition(final EffectiveSubscriptionInternalEvent curEvent);

    public void onBlockingState(final BlockingTransitionInternalEvent curEvent);

    public void onInvoiceCreation(final InvoiceCreationInternalEvent curEvent);

    public void onEmptyInvoice(final NullInvoiceInternalEvent curEvent);

    public void onPaymentInfo(final PaymentInfoInternalEvent curEvent);

    public void onPaymentError(final PaymentErrorInternalEvent curEvent);

    public void onPaymentPluginError(final PaymentPluginErrorInternalEvent curEvent);

    public void onInvoicePaymentInfo(final InvoicePaymentInfoInternalEvent curEvent);

    public void onInvoicePaymentError(final InvoicePaymentErrorInternalEvent curEvent);
}
