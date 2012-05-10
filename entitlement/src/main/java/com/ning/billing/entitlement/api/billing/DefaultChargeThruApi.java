/*
w * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.entitlement.api.billing;

import java.util.UUID;

import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.TableName;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.SubscriptionSqlDao;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;

public class DefaultChargeThruApi implements ChargeThruApi {
	private static final Logger log = LoggerFactory.getLogger(DefaultChargeThruApi.class);

    private final EntitlementDao entitlementDao;
    private final SubscriptionFactory subscriptionFactory;

    @Inject
    public DefaultChargeThruApi(final SubscriptionFactory subscriptionFactory, final EntitlementDao dao) {
        super();
        this.subscriptionFactory = subscriptionFactory;
        this.entitlementDao = dao;
    }
    
    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
        return entitlementDao.getAccountIdFromSubscriptionId(subscriptionId);
    }

    @Override
    public void setChargedThroughDate(final UUID subscriptionId, final DateTime ctd, CallContext context) {
        SubscriptionData subscription = (SubscriptionData) entitlementDao.getSubscriptionFromId(subscriptionFactory, subscriptionId);

        SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
            .setChargedThroughDate(ctd)
            .setPaidThroughDate(subscription.getPaidThroughDate());
        entitlementDao.updateChargedThroughDate(new SubscriptionData(builder), context);
    }

    @Override
    public void setChargedThroughDateFromTransaction(final Transmogrifier transactionalDao, final UUID subscriptionId,
                                                     final DateTime ctd, final CallContext context) {
        SubscriptionSqlDao subscriptionSqlDao = transactionalDao.become(SubscriptionSqlDao.class);
        SubscriptionData subscription = (SubscriptionData) subscriptionSqlDao.getSubscriptionFromId(subscriptionId.toString());

        if (subscription == null) {
            log.warn("Subscription not found when setting CTD.");
        } else {
            DateTime chargedThroughDate = subscription.getChargedThroughDate();
            if (chargedThroughDate == null || chargedThroughDate.isBefore(ctd)) {
                subscriptionSqlDao.updateChargedThroughDate(subscriptionId.toString(),
                        ctd.toDate(), context);

                Long recordId = subscriptionSqlDao.getRecordId(TableName.SUBSCRIPTIONS, subscriptionId.toString());
                EntityAudit audit = new EntityAudit(recordId, ChangeType.UPDATE);
                subscriptionSqlDao.insertAuditFromTransaction(TableName.SUBSCRIPTIONS, audit, context);
            }
        }
    }
}
