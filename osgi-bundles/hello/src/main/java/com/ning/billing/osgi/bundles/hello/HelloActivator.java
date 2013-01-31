/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.osgi.bundles.hello;

import java.math.BigDecimal;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.beatrix.bus.api.ExtBusEvent;
import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.eventbus.Subscribe;

public class HelloActivator implements BundleActivator {

    private volatile boolean isRunning;
    private volatile ServiceRegistration paymentInfoPluginRegistration;

    @Override
    public void start(final BundleContext context) {
        isRunning = true;
        System.out.println("Hello world from HelloActivator!");

        doSomeWorkWithKillbillApis(context);
        registerForKillbillEvents(context);
        registerPaymentApi(context);
    }

    @Override
    public void stop(final BundleContext context) {
        isRunning = false;
        System.out.println("Good bye world from HelloActivator!");
    }

    private void doSomeWorkWithKillbillApis(final BundleContext context) {
        final TenantContext tenantContext = new TenantContext() {
            @Override
            public UUID getTenantId() {
                return new UUID(12, 42);
            }
        };

        final Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    @SuppressWarnings("unchecked")
                    final ServiceReference<AccountUserApi> accountUserApiReference = (ServiceReference<AccountUserApi>) context.getServiceReference(AccountUserApi.class.getName());
                    final AccountUserApi accountUserApi = context.getService(accountUserApiReference);

                    try {
                        final List<Account> accounts = accountUserApi.getAccounts(tenantContext);
                        System.out.println("Found " + accounts.size() + " accounts");
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        System.err.println("Interrupted in HelloActivator");
                    } catch (Exception e) {
                        System.err.println("Error in HelloActivator: " + e.getLocalizedMessage());
                    } finally {
                        if (accountUserApiReference != null) {
                            context.ungetService(accountUserApiReference);
                        }
                    }
                }
            }
        });
        th.start();
    }

    private void registerForKillbillEvents(final BundleContext context) {
        @SuppressWarnings("unchecked")
        final ServiceReference<ExternalBus> externalBusReference = (ServiceReference<ExternalBus>) context.getServiceReference(ExternalBus.class.getName());
        try {
            final ExternalBus externalBus = context.getService(externalBusReference);
            externalBus.register(this);
        } catch (Exception e) {
            System.err.println("Error in HelloActivator: " + e.getLocalizedMessage());
        } finally {
            if (externalBusReference != null) {
                context.ungetService(externalBusReference);
            }
        }
    }

    private void registerPaymentApi(final BundleContext context) {
        final Dictionary props = new Hashtable();
        props.put("name", "hello");

        this.paymentInfoPluginRegistration = context.registerService(PaymentPluginApi.class.getName(), new PaymentPluginApi() {
            @Override
            public String getName() {
                return "helloName";
            }

            @Override
            public PaymentInfoPlugin processPayment(final String externalAccountKey, final UUID paymentId, final BigDecimal amount, final CallContext context) throws PaymentPluginApiException {
                return null;
            }

            @Override
            public PaymentInfoPlugin getPaymentInfo(final UUID paymentId, final TenantContext context) throws PaymentPluginApiException {
                return null;
            }

            @Override
            public void processRefund(final Account account, final UUID paymentId, final BigDecimal refundAmount, final CallContext context) throws PaymentPluginApiException {
            }

            @Override
            public int getNbRefundForPaymentAmount(final Account account, final UUID paymentId, final BigDecimal refundAmount, final TenantContext context) throws PaymentPluginApiException {
                return 0;
            }

            @Override
            public String createPaymentProviderAccount(final Account account, final CallContext context) throws PaymentPluginApiException {
                return null;
            }

            @Override
            public List<PaymentMethodPlugin> getPaymentMethodDetails(final String accountKey, final TenantContext context) throws PaymentPluginApiException {
                return null;
            }

            @Override
            public PaymentMethodPlugin getPaymentMethodDetail(final String accountKey, final String externalPaymentMethodId, final TenantContext context) throws PaymentPluginApiException {
                return null;
            }

            @Override
            public String addPaymentMethod(final String accountKey, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final CallContext context) throws PaymentPluginApiException {
                return null;
            }

            @Override
            public void updatePaymentMethod(final String accountKey, final PaymentMethodPlugin paymentMethodProps, final CallContext context) throws PaymentPluginApiException {
            }

            @Override
            public void deletePaymentMethod(final String accountKey, final String externalPaymentMethodId, final CallContext context) throws PaymentPluginApiException {
            }

            @Override
            public void setDefaultPaymentMethod(final String accountKey, final String externalPaymentId, final CallContext context) throws PaymentPluginApiException {
            }
        }, props);
    }

    @Subscribe
    public void handleKillbillEvent(final ExtBusEvent killbillEvent) {
        System.out.println("Received external event " + killbillEvent.toString());
    }
}
