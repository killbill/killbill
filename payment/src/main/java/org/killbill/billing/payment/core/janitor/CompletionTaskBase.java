/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.io.IOException;

import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.PaymentInternalEvent;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.notificationq.api.NotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class CompletionTaskBase<T> implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CompletionTaskBase.class);

    protected final PaymentConfig paymentConfig;
    protected final Clock clock;
    protected final PaymentDao paymentDao;
    protected final InternalCallContextFactory internalCallContextFactory;
    protected final PaymentStateMachineHelper paymentStateMachineHelper;
    protected final PaymentControlStateMachineHelper retrySMHelper;
    protected final AccountInternalApi accountInternalApi;
    protected final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;
    protected final GlobalLocker locker;

    protected NotificationQueue janitorQueue;

    private volatile boolean isStopped;

    public CompletionTaskBase(final InternalCallContextFactory internalCallContextFactory, final PaymentConfig paymentConfig,
                              final PaymentDao paymentDao, final Clock clock, final PaymentStateMachineHelper paymentStateMachineHelper,
                              final PaymentControlStateMachineHelper retrySMHelper, final AccountInternalApi accountInternalApi,
                              final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry, final GlobalLocker locker) {
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentConfig = paymentConfig;
        this.paymentDao = paymentDao;
        this.clock = clock;
        this.paymentStateMachineHelper = paymentStateMachineHelper;
        this.retrySMHelper = retrySMHelper;
        this.accountInternalApi = accountInternalApi;
        this.pluginRegistry = pluginRegistry;
        this.locker = locker;
        this.isStopped = false;
    }

    @Override
    public void run() {
        if (isStopped) {
            log.info("Janitor was requested to stop");
            return;
        }
        final Iterable<T> items = getItemsForIteration();
        for (final T item : items) {
            if (isStopped) {
                log.info("Janitor was requested to stop");
                return;
            }
            try {
                doIteration(item);
            } catch (final IllegalStateException e) {
                log.warn(e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        this.isStopped = true;
    }

    public abstract Iterable<T> getItemsForIteration();

    public abstract void doIteration(final T item);

    public abstract void processPaymentEvent(final PaymentInternalEvent event, final NotificationQueue janitorQueue) throws IOException;

    public void attachJanitorQueue(final NotificationQueue janitorQueue) {
        this.janitorQueue = janitorQueue;
    }

    public interface JanitorIterationCallback {
        public <T> T doIteration();
    }

    protected <T> T doJanitorOperationWithAccountLock(final JanitorIterationCallback callback, final InternalTenantContext internalTenantContext) {
        GlobalLock lock = null;
        try {
            final ImmutableAccountData account = accountInternalApi.getImmutableAccountDataByRecordId(internalTenantContext.getAccountRecordId(), internalTenantContext);
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), account.getExternalKey(), paymentConfig.getMaxGlobalLockRetries());
            return callback.doIteration();
        } catch (AccountApiException e) {
            log.warn("Error retrieving accountRecordId='{}'", internalTenantContext.getAccountRecordId(), e);
        } catch (LockFailedException e) {
            log.warn("Error locking accountRecordId='{}'", internalTenantContext.getAccountRecordId(), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return null;
    }

    protected CallContext createCallContext(final String taskName, final InternalTenantContext internalTenantContext) {
        final TenantContext tenantContext = internalCallContextFactory.createTenantContext(internalTenantContext);
        return new DefaultCallContext(tenantContext.getTenantId(), taskName, CallOrigin.INTERNAL, UserType.SYSTEM, UUIDs.randomUUID(), clock);
    }
}
