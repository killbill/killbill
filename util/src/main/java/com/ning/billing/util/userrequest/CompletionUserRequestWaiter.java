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

import java.util.List;
import java.util.concurrent.TimeoutException;

import com.ning.billing.util.events.AccountChangeInternalEvent;
import com.ning.billing.util.events.AccountCreationInternalEvent;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.events.NullInvoiceInternalEvent;
import com.ning.billing.util.events.PaymentErrorInternalEvent;
import com.ning.billing.util.events.PaymentInfoInternalEvent;

public interface CompletionUserRequestWaiter {

    public List<BusInternalEvent> waitForCompletion(final long timeoutMilliSec) throws InterruptedException, TimeoutException;

    public void onAccountCreation(final AccountCreationInternalEvent curEvent);

    public void onAccountChange(final AccountChangeInternalEvent curEvent);

    public void onSubscriptionTransition(final EffectiveSubscriptionInternalEvent curEventEffective);

    public void onInvoiceCreation(final InvoiceCreationInternalEvent curEvent);

    public void onEmptyInvoice(final NullInvoiceInternalEvent curEvent);

    public void onPaymentInfo(final PaymentInfoInternalEvent curEvent);

    public void onPaymentError(final PaymentErrorInternalEvent curEvent);
}
