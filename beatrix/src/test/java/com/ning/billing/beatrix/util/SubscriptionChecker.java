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

package com.ning.billing.beatrix.util;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.ObjectType;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransition;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransitionData;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.NonEntityDao;

public class SubscriptionChecker {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionChecker.class);

    private final SubscriptionBaseInternalApi subscriptionApi;
    private final AuditChecker auditChecker;
    private final NonEntityDao nonEntityDao;

    @Inject
    public SubscriptionChecker(final SubscriptionBaseInternalApi subscriptionApi, final AuditChecker auditChecker, final NonEntityDao nonEntityDao) {
        this.subscriptionApi = subscriptionApi;
        this.auditChecker = auditChecker;
        this.nonEntityDao = nonEntityDao;
    }

    public SubscriptionBaseBundle checkBundleNoAudits(final UUID bundleId, final UUID expectedAccountId, final String expectedKey, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBaseBundle bundle = subscriptionApi.getBundleFromId(bundleId, context);
        Assert.assertNotNull(bundle);
        Assert.assertEquals(bundle.getAccountId(), expectedAccountId);
        Assert.assertEquals(bundle.getExternalKey(), expectedKey);
        return bundle;
    }

    public SubscriptionBase checkSubscriptionCreated(final UUID subscriptionId, final InternalCallContext context) throws SubscriptionBaseApiException {
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT);
        final CallContext callContext = context.toCallContext(tenantId);

        final SubscriptionBase subscription = subscriptionApi.getSubscriptionFromId(subscriptionId, context);
        Assert.assertNotNull(subscription);
        auditChecker.checkSubscriptionCreated(subscription.getBundleId(), subscriptionId, callContext);

        List<SubscriptionBaseTransition> subscriptionEvents = getSubscriptionEvents(subscription);
        Assert.assertTrue(subscriptionEvents.size() >= 1);
        auditChecker.checkSubscriptionEventCreated(subscription.getBundleId(), ((SubscriptionBaseTransitionData) subscriptionEvents.get(0)).getId(), callContext);

        auditChecker.checkBundleCreated(subscription.getBundleId(), callContext);
        return subscription;
    }

    private List<SubscriptionBaseTransition> getSubscriptionEvents(final SubscriptionBase subscription) {
        return subscription.getAllTransitions();
    }
}
