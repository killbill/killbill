/*
 * Copyright 2010-2012 Ning, Inc.
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

import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.entitlement.api.timeline.EntitlementRepairException;
import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.AuditLogsForBundles;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.CallContext;

import com.google.inject.Inject;

public class AuditChecker {

    private static final Logger log = LoggerFactory.getLogger(AuditChecker.class);

    private final AuditUserApi auditUserApi;

    @Inject
    public AuditChecker(final AuditUserApi auditUserApi) {
        this.auditUserApi = auditUserApi;
    }

    // Pass the call context used to create the bundle
    public void checkBundleCreated(final UUID bundleId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);

        Assert.assertEquals(auditLogsForBundles.getBundlesAuditLogs().keySet().size(), 1);
        Assert.assertEquals(auditLogsForBundles.getBundlesAuditLogs().get(bundleId).size(), 1);
        checkAuditLog(ChangeType.INSERT, context, auditLogsForBundles.getBundlesAuditLogs().get(bundleId).get(0));
    }

    // Pass the call context used to update the bundle
    public void checkBundleUpdated(final UUID bundleId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);

        Assert.assertEquals(auditLogsForBundles.getBundlesAuditLogs().keySet().size(), 1);
        Assert.assertEquals(auditLogsForBundles.getBundlesAuditLogs().get(bundleId).size(), 2);
        checkAuditLog(ChangeType.INSERT, auditLogsForBundles.getBundlesAuditLogs().get(bundleId).get(0));
        checkAuditLog(ChangeType.UPDATE, context, auditLogsForBundles.getBundlesAuditLogs().get(bundleId).get(1));
    }

    // Pass the call context used to create the subscription
    public void checkSubscriptionCreated(final UUID bundleId, final UUID subscriptionId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);

        Assert.assertEquals(auditLogsForBundles.getSubscriptionsAuditLogs().keySet().size(), 1);
        Assert.assertEquals(auditLogsForBundles.getSubscriptionsAuditLogs().get(subscriptionId).size(), 1);
        checkAuditLog(ChangeType.INSERT, context, auditLogsForBundles.getSubscriptionsAuditLogs().get(subscriptionId).get(0));
    }

    // Pass the call context used to update the subscription
    public void checkSubscriptionUpdated(final UUID bundleId, final UUID subscriptionId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);

        Assert.assertEquals(auditLogsForBundles.getSubscriptionsAuditLogs().keySet().size(), 1);
        Assert.assertEquals(auditLogsForBundles.getSubscriptionsAuditLogs().get(subscriptionId).size(), 2);
        checkAuditLog(ChangeType.INSERT, auditLogsForBundles.getSubscriptionsAuditLogs().get(subscriptionId).get(0));
        checkAuditLog(ChangeType.UPDATE, context, auditLogsForBundles.getSubscriptionsAuditLogs().get(subscriptionId).get(1));
    }

    // Pass the call context used to create the subscription event
    public void checkSubscriptionEventCreated(final UUID bundleId, final UUID subscriptionEventId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);

        Assert.assertEquals(auditLogsForBundles.getSubscriptionEventsAuditLogs().keySet().size(), 1);
        Assert.assertEquals(auditLogsForBundles.getSubscriptionEventsAuditLogs().get(subscriptionEventId).size(), 1);
        checkAuditLog(ChangeType.INSERT, context, auditLogsForBundles.getSubscriptionEventsAuditLogs().get(subscriptionEventId).get(0));
    }

    // Pass the call context used to update the subscription event
    public void checkSubscriptionEventUpdated(final UUID bundleId, final UUID subscriptionEventId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);

        Assert.assertEquals(auditLogsForBundles.getSubscriptionEventsAuditLogs().keySet().size(), 1);
        Assert.assertEquals(auditLogsForBundles.getSubscriptionEventsAuditLogs().get(subscriptionEventId).size(), 2);
        checkAuditLog(ChangeType.INSERT, auditLogsForBundles.getSubscriptionEventsAuditLogs().get(subscriptionEventId).get(0));
        checkAuditLog(ChangeType.UPDATE, context, auditLogsForBundles.getSubscriptionEventsAuditLogs().get(subscriptionEventId).get(1));
    }

    private AuditLogsForBundles getAuditLogsForBundle(final UUID bundleId, final CallContext context) {
        try {
            return auditUserApi.getAuditLogsForBundle(bundleId, AuditLevel.FULL, context);
        } catch (EntitlementRepairException e) {
            Assert.fail(e.toString());
            return null;
        }
    }

    private void checkAuditLog(final ChangeType insert, final AuditLog auditLog) {
        checkAuditLog(insert, null, auditLog);
    }

    private void checkAuditLog(final ChangeType changeType, @Nullable final CallContext context, final AuditLog auditLog) {
        Assert.assertEquals(auditLog.getChangeType(), changeType);

        if (context != null) {
            Assert.assertEquals(auditLog.getUserName(), context.getUserName());
            Assert.assertEquals(auditLog.getReasonCode(), context.getReasonCode());
            // TODO check 'Next Billing Date' and 'Transition' - add comment, maybe internal reason code and token
            Assert.assertEquals(auditLog.getComment(), context.getComments());
            Assert.assertEquals(auditLog.getUserToken(), context.getUserToken().toString());
            Assert.assertEquals(auditLog.getCreatedDate(), context.getCreatedDate());
        }
    }
}
