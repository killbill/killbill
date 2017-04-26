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

package org.killbill.billing.beatrix.util;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionData;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.dao.NonEntityDao;

public class SubscriptionChecker {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionChecker.class);

    private final SubscriptionBaseInternalApi subscriptionApi;
    private final AuditChecker auditChecker;
    private final NonEntityDao nonEntityDao;
    private final CacheController<String, UUID> objectIdCacheController;

    @Inject
    public SubscriptionChecker(final SubscriptionBaseInternalApi subscriptionApi, final AuditChecker auditChecker, final NonEntityDao nonEntityDao, final CacheControllerDispatcher cacheControllerDispatcher) {
        this.subscriptionApi = subscriptionApi;
        this.auditChecker = auditChecker;
        this.nonEntityDao = nonEntityDao;
        objectIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID);
    }

    public SubscriptionBaseBundle checkBundleNoAudits(final UUID bundleId, final UUID expectedAccountId, final String expectedKey, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBaseBundle bundle = subscriptionApi.getBundleFromId(bundleId, context);
        Assert.assertNotNull(bundle);
        Assert.assertEquals(bundle.getAccountId(), expectedAccountId);
        Assert.assertEquals(bundle.getExternalKey(), expectedKey);
        return bundle;
    }

    public SubscriptionBase checkSubscriptionCreated(final UUID subscriptionId, final InternalCallContext context) throws SubscriptionBaseApiException {
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT, objectIdCacheController);
        final CallContext callContext = context.toCallContext(null, tenantId);

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
