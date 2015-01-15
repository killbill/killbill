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

package org.killbill.billing.payment.core.janitor;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginRoutingPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.RetryStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class CompletionTaskBase<T> implements Runnable {

    protected Logger log = LoggerFactory.getLogger(CompletionTaskBase.class);

    private Janitor janitor;
    private final String taskName;

    protected final PaymentConfig paymentConfig;
    protected final Clock clock;
    protected final PaymentDao paymentDao;
    protected final InternalCallContext completionTaskCallContext;
    protected final InternalCallContextFactory internalCallContextFactory;
    protected final NonEntityDao nonEntityDao;
    protected final PaymentStateMachineHelper paymentStateMachineHelper;
    protected final RetryStateMachineHelper retrySMHelper;
    protected final CacheControllerDispatcher controllerDispatcher;
    protected final AccountInternalApi accountInternalApi;
    protected final PluginRoutingPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner;
    protected final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;


    public CompletionTaskBase(final Janitor janitor, final InternalCallContextFactory internalCallContextFactory, final PaymentConfig paymentConfig,
                              final NonEntityDao nonEntityDao, final PaymentDao paymentDao, final Clock clock, final PaymentStateMachineHelper paymentStateMachineHelper,
                              final RetryStateMachineHelper retrySMHelper, final CacheControllerDispatcher controllerDispatcher, final AccountInternalApi accountInternalApi,
                              final PluginRoutingPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner, final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry) {
        this.janitor = janitor;
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentConfig = paymentConfig;
        this.nonEntityDao = nonEntityDao;
        this.paymentDao = paymentDao;
        this.clock = clock;
        this.paymentStateMachineHelper = paymentStateMachineHelper;
        this.retrySMHelper = retrySMHelper;
        this.controllerDispatcher = controllerDispatcher;
        this.accountInternalApi = accountInternalApi;
        this.pluginControlledPaymentAutomatonRunner = pluginControlledPaymentAutomatonRunner;
        this.pluginRegistry = pluginRegistry;
        // Limit the length of the username in the context (limited to 50 characters)
        this.taskName = this.getClass().getSimpleName();
        this.completionTaskCallContext = internalCallContextFactory.createInternalCallContext((Long) null, (Long) null, taskName, CallOrigin.INTERNAL, UserType.SYSTEM, UUID.randomUUID());
    }

    @Override
    public void run() {

        if (janitor.isStopped()) {
            log.info("Janitor Task " + taskName + " was requested to stop");
            return;
        }
        final List<T> items = getItemsForIteration();
        for (T item : items) {
            if (janitor.isStopped()) {
                log.info("Janitor Task " + taskName + " was requested to stop");
                return;
            }
            try {
                doIteration(item);
            } catch(IllegalStateException e) {
                log.warn(e.getMessage());
            }
        }
    }

    public abstract List<T> getItemsForIteration();

    public abstract void doIteration(final T item);

    protected CallContext createCallContext(final String taskName, final InternalTenantContext tenantContext) {
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(tenantContext.getTenantRecordId(), ObjectType.TENANT, controllerDispatcher.getCacheController(CacheType.OBJECT_ID));
        final CallContext callContext = new DefaultCallContext(tenantId, taskName, CallOrigin.INTERNAL, UserType.SYSTEM, UUID.randomUUID(), clock);
        return callContext;
    }


    protected DateTime getCreatedDateBefore() {
        final long delayBeforeNowMs = paymentConfig.getJanitorPendingCleanupTime().getMillis();
        return clock.getUTCNow().minusMillis((int) delayBeforeNowMs);
    }
}
