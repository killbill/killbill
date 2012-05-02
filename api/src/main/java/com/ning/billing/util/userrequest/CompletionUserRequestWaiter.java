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
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.ning.billing.account.api.AccountChangeEvent;
import com.ning.billing.account.api.AccountCreationEvent;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.invoice.api.EmptyInvoiceEvent;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.util.bus.BusEvent;

public interface CompletionUserRequestWaiter {

    public List<BusEvent> waitForCompletion(final long timeoutMilliSec) throws InterruptedException, TimeoutException;
    
    public void onAccountCreation(final AccountCreationEvent curEvent);

    public void onAccountChange(final AccountChangeEvent curEvent);

    public void onSubscriptionTransition(final SubscriptionEvent curEvent);    

    public void onInvoiceCreation(final InvoiceCreationEvent curEvent);    
    
    public void onEmptyInvoice(final EmptyInvoiceEvent curEvent);        

    public void onPaymentInfo(final PaymentInfoEvent curEvent);    

    public void onPaymentError(final PaymentErrorEvent curEvent);    
}
