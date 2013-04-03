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

package com.ning.billing.osgi.bundles.analytics;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.osgi.service.log.LogService;

import com.ning.billing.beatrix.bus.api.ExtBusEvent;
import com.ning.billing.commons.locker.GlobalLock;
import com.ning.billing.commons.locker.GlobalLocker;
import com.ning.billing.commons.locker.mysql.MySqlGlobalLocker;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class AnalyticsListener extends BusinessAnalyticsBase implements OSGIKillbillEventHandler {

    private static final int NB_LOCK_TRY = 5;

    private final BusinessAccountDao bacDao;
    private final BusinessSubscriptionTransitionDao bstDao;
    private final BusinessInvoiceDao binDao;
    private final BusinessInvoicePaymentDao bipDao;
    private final BusinessOverdueStatusDao bosDao;
    private final GlobalLocker locker;

    public AnalyticsListener(final OSGIKillbillLogService logService, final OSGIKillbillAPI osgiKillbillAPI, final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillAPI);

        this.bacDao = new BusinessAccountDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.bstDao = new BusinessSubscriptionTransitionDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.binDao = new BusinessInvoiceDao(logService, osgiKillbillAPI, osgiKillbillDataSource, bacDao);
        this.bipDao = new BusinessInvoicePaymentDao(logService, osgiKillbillAPI, osgiKillbillDataSource, bacDao, binDao);
        this.bosDao = new BusinessOverdueStatusDao(logService, osgiKillbillAPI, osgiKillbillDataSource);

        this.locker = new MySqlGlobalLocker(osgiKillbillDataSource.getDataSource());
    }

    @Override
    public void handleKillbillEvent(final ExtBusEvent killbillEvent) {
        final CallContext callContext = new AnalyticsCallContext(killbillEvent);

        try {
            switch (killbillEvent.getEventType()) {
                case ACCOUNT_CREATION:
                case ACCOUNT_CHANGE:
                    handleAccountEvent(killbillEvent, callContext);
                    break;
                case SUBSCRIPTION_CREATION:
                case SUBSCRIPTION_CHANGE:
                case SUBSCRIPTION_CANCEL:
                    handleSubscriptionEvent(killbillEvent, callContext);
                    break;
                case OVERDUE_CHANGE:
                    handleOverdueEvent(killbillEvent, callContext);
                    break;
                case INVOICE_CREATION:
                    handleInvoiceEvent(killbillEvent, callContext);
                    break;
                case PAYMENT_SUCCESS:
                case PAYMENT_FAILED:
                    handlePaymentEvent(killbillEvent, callContext);
                    break;
                default:
                    // TODO invoice adjustments
                    // TODO refunds
                    // TODO tags and custom fields
                    break;
            }
        } catch (AnalyticsRefreshException e) {
            logService.log(LogService.LOG_WARNING, "Refresh triggered by event " + killbillEvent + " failed", e);
        }
    }

    private void handleAccountEvent(final ExtBusEvent killbillEvent, final CallContext callContext) throws AnalyticsRefreshException {
        updateWithAccountLock(killbillEvent, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                bacDao.update(killbillEvent.getAccountId(), callContext);
                return null;
            }
        });
    }

    private void handleSubscriptionEvent(final ExtBusEvent killbillEvent, final CallContext callContext) throws AnalyticsRefreshException {
        final UUID bundleId = getBundleIdFromEvent(killbillEvent, callContext);
        updateWithAccountLock(killbillEvent, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                bstDao.update(bundleId, callContext);
                return null;
            }
        });
    }

    private void handleInvoiceEvent(final ExtBusEvent killbillEvent, final CallContext callContext) throws AnalyticsRefreshException {
        updateWithAccountLock(killbillEvent, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                binDao.update(killbillEvent.getAccountId(), callContext);
                return null;
            }
        });
    }

    private void handlePaymentEvent(final ExtBusEvent killbillEvent, final CallContext callContext) throws AnalyticsRefreshException {
        final UUID paymentId = getPaymentIdFromEvent(killbillEvent, callContext);
        updateWithAccountLock(killbillEvent, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                bipDao.update(killbillEvent.getAccountId(), paymentId, callContext);
                return null;
            }
        });
    }

    private void handleOverdueEvent(final ExtBusEvent killbillEvent, final CallContext callContext) throws AnalyticsRefreshException {
        updateWithAccountLock(killbillEvent, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                bosDao.update(killbillEvent.getObjectType(), killbillEvent.getObjectId(), callContext);
                return null;
            }
        });
    }

    private UUID getBundleIdFromEvent(final ExtBusEvent killbillEvent, final TenantContext tenantContext) throws AnalyticsRefreshException {
        switch (killbillEvent.getObjectType()) {
            case BUNDLE:
                return killbillEvent.getObjectId();
            case SUBSCRIPTION:
                return getSubscription(killbillEvent.getObjectId(), tenantContext).getBundleId();
            default:
                return null;
        }
    }

    private UUID getPaymentIdFromEvent(final ExtBusEvent killbillEvent, final TenantContext tenantContext) {
        switch (killbillEvent.getObjectType()) {
            case PAYMENT:
                return killbillEvent.getObjectId();
            default:
                return null;
        }
    }

    private static final class AnalyticsCallContext implements CallContext {

        private static final String USER_NAME = AnalyticsListener.class.getName();

        private final ExtBusEvent killbillEvent;
        private final DateTime now;

        private AnalyticsCallContext(final ExtBusEvent killbillEvent) {
            this.killbillEvent = killbillEvent;
            this.now = new DateTime(DateTimeZone.UTC);
        }

        @Override
        public UUID getUserToken() {
            return UUID.randomUUID();
        }

        @Override
        public String getUserName() {
            return USER_NAME;
        }

        @Override
        public CallOrigin getCallOrigin() {
            return CallOrigin.EXTERNAL;
        }

        @Override
        public UserType getUserType() {
            return UserType.SYSTEM;
        }

        @Override
        public String getReasonCode() {
            return killbillEvent.getEventType().toString();
        }

        @Override
        public String getComments() {
            return "eventType=" + killbillEvent.getEventType() + ", objectType="
                   + killbillEvent.getObjectType() + ", objectId=" + killbillEvent.getObjectId() + ", accountId="
                   + killbillEvent.getAccountId() + ", tenantId=" + killbillEvent.getTenantId();
        }

        @Override
        public DateTime getCreatedDate() {
            return now;
        }

        @Override
        public DateTime getUpdatedDate() {
            return now;
        }

        @Override
        public UUID getTenantId() {
            return killbillEvent.getTenantId();
        }
    }

    private <T> T updateWithAccountLock(final ExtBusEvent killbillEvent, final Callable<T> task) {
        GlobalLock lock = null;
        try {
            final String lockKey = killbillEvent.getAccountId() == null ? "0" : killbillEvent.getAccountId().toString();
            lock = locker.lockWithNumberOfTries("ACCOUNT_FOR_ANALYTICS", lockKey, NB_LOCK_TRY);
            return task.call();
        } catch (Exception e) {
            logService.log(LogService.LOG_WARNING, "Exception while refreshing analytics tables", e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }

        return null;
    }
}
