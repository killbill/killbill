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

package com.ning.billing.payment;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.payment.api.DefaultPaymentErrorEvent;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;

import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.LockFailedException;
import com.ning.billing.util.globallocker.GlobalLocker.LockerService;

public class RequestProcessor {

    public static final String PAYMENT_PROVIDER_KEY = "paymentProvider";

    /*
    private final static int NB_PAYMENT_THREADS = 3; // STEPH
    private final static String PAYMENT_GROUP_NAME = "payment-grp";
    private final static String PAYMENT_TH_NAME = "payment-th";
*/

    private final AccountUserApi accountUserApi;
    private final PaymentApi paymentApi;
    private final Clock clock;

    private static final Logger log = LoggerFactory.getLogger(RequestProcessor.class);

    @Inject
    public RequestProcessor(final Clock clock,
            final AccountUserApi accountUserApi,
            final PaymentApi paymentApi,           
            final GlobalLocker locker) {
        this.clock = clock;
        this.accountUserApi = accountUserApi;
        this.paymentApi = paymentApi;

        /*
        this.executor = Executors.newFixedThreadPool(NB_PAYMENT_THREADS, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(new ThreadGroup(PAYMENT_GROUP_NAME),
                        r,
                        PAYMENT_TH_NAME);
            }
        });
         */
    }


    @Subscribe
    public void processInvoiceEvent(InvoiceCreationEvent event) {
        log.info("Received invoice creation notification for account {} and invoice {}", event.getAccountId(), event.getInvoiceId());

        Account account = null;        
        try {
            account = accountUserApi.getAccountById(event.getAccountId());
            if (account == null) {
                log.error("Failed to process invoice, account {} does not exist!", event.getAccountId());
                return;
            }

            CallContext context = new DefaultCallContext("PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken(), clock);
            paymentApi.createPayment(account, event.getInvoiceId(), context);
        } catch(AccountApiException e) {
            log.error("Failed to process invoice payment", e);
        } catch (PaymentApiException e) {
            log.error("Failed to process invoice payment", e);            
        }
    }
}


