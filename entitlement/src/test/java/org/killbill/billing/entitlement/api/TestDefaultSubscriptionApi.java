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

package org.killbill.billing.entitlement.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.ChangeType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestDefaultSubscriptionApi extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "Verify blocking states are exposed in SubscriptionBundle")
    public void testBlockingStatesInTimelineApi() throws Exception {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.BLOCK);
        final Entitlement entitlement1 = entitlementApi.createBaseEntitlement(account.getId(), spec, UUID.randomUUID().toString(), initialDate, callContext);
        // Sleep 1 sec so created date are apparts from each other and ordering in the bundle does not default on the UUID which is random.
        try {Thread.sleep(1000); } catch (InterruptedException ignore) {};
        final Entitlement entitlement2 = entitlementApi.createBaseEntitlement(account.getId(), spec, UUID.randomUUID().toString(), initialDate, callContext);
        entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "stateName", "service", false, false, false, clock.getUTCNow()),
                                                                        internalCallContextFactory.createInternalCallContext(account.getId(), callContext));
        assertListenerStatus();

        final List<SubscriptionBundle> bundles = subscriptionApi.getSubscriptionBundlesForAccountId(account.getId(), callContext);
        Assert.assertEquals(bundles.size(), 2);

        // This will test the ordering as well
        subscriptionBundleChecker(bundles, initialDate, entitlement1, 0);
        subscriptionBundleChecker(bundles, initialDate, entitlement2, 1);
    }

    private void subscriptionBundleChecker(final List<SubscriptionBundle> bundles, final LocalDate initialDate, final Entitlement entitlement, final int idx) {
        Assert.assertEquals(bundles.get(idx).getId(), entitlement.getBundleId());
        Assert.assertEquals(bundles.get(idx).getSubscriptions().size(), 1);
        Assert.assertEquals(bundles.get(idx).getSubscriptions().get(0).getId(), entitlement.getId());
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().size(), 4);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(0).getEffectiveDate(), initialDate);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(1).getEffectiveDate(), initialDate);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getEffectiveDate(), initialDate);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getServiceName(), "service");
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getServiceStateName(), "stateName");
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(3).getEffectiveDate(), new LocalDate(2013, 9, 6));
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(3).getSubscriptionEventType(), SubscriptionEventType.PHASE);
    }

    @Test(groups = "slow")
    public void testWithMultipleBundle() throws AccountApiException, SubscriptionApiException, EntitlementApiException {
        final String externalKey = "fooXXX";

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, externalKey, initialDate, callContext);
        assertListenerStatus();
        assertEquals(entitlement.getAccountId(), account.getId());
        assertEquals(entitlement.getExternalKey(), externalKey);

        assertEquals(entitlement.getEffectiveStartDate(), initialDate);
        assertNull(entitlement.getEffectiveEndDate());

        final List<SubscriptionBundle> bundles = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, callContext);
        assertEquals(bundles.size(), 1);

        final SubscriptionBundle activeBundle = subscriptionApi.getActiveSubscriptionBundleForExternalKey(externalKey, callContext);
        assertEquals(activeBundle.getId(), entitlement.getBundleId());

        // Cancel entitlement
        clock.addDays(3);
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        entitlement.cancelEntitlementWithDate(new LocalDate(clock.getUTCNow(), account.getTimeZone()), true, callContext);
        assertListenerStatus();

        try {
            subscriptionApi.getActiveSubscriptionBundleForExternalKey(externalKey, callContext);
            Assert.fail("Expected getActiveSubscriptionBundleForExternalKey to fail after cancellation");
        } catch (SubscriptionApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_GET_INVALID_BUNDLE_KEY.getCode());

        }

        clock.addDays(1);
        // Re-create a new bundle with same externalKey
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement2 = entitlementApi.createBaseEntitlement(account.getId(), spec2, externalKey, new LocalDate(clock.getUTCNow(), account.getTimeZone()), callContext);
        assertListenerStatus();
        assertEquals(entitlement2.getAccountId(), account.getId());
        assertEquals(entitlement2.getExternalKey(), externalKey);

        final List<SubscriptionBundle> bundles2 = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, callContext);
        assertEquals(bundles2.size(), 2);

        SubscriptionBundle firstbundle = bundles2.get(0);
        assertEquals(firstbundle.getSubscriptions().size(), 1);
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveEndDate(), new LocalDate(2013, 8, 10));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingEndDate(), new LocalDate(2013, 8, 10));

        SubscriptionBundle secondbundle = bundles2.get(1);
        assertEquals(secondbundle.getSubscriptions().size(), 1);
        assertEquals(secondbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 11));
        assertEquals(secondbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 11));
        assertNull(secondbundle.getSubscriptions().get(0).getEffectiveEndDate());
        assertNull(secondbundle.getSubscriptions().get(0).getBillingEndDate());
        assertEquals(secondbundle.getOriginalCreatedDate().compareTo(firstbundle.getCreatedDate()), 0);

        final List<SubscriptionBundle> bundles2Again = subscriptionApi.getSubscriptionBundlesForAccountIdAndExternalKey(account.getId(), externalKey, callContext);
        assertEquals(bundles2Again.size(), 2);

        clock.addDays(3);

        final Account account2 = accountApi.createAccount(getAccountData(7), callContext);

        testListener.pushExpectedEvents(NextEvent.TRANSFER, NextEvent.CANCEL, NextEvent.BLOCK);
        entitlementApi.transferEntitlements(account.getId(), account2.getId(), externalKey, new LocalDate(clock.getUTCNow(), account.getTimeZone()), callContext);
        assertListenerStatus();

        final List<SubscriptionBundle> bundles3 = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, callContext);
        assertEquals(bundles3.size(), 3);

        firstbundle = bundles3.get(0);
        assertEquals(firstbundle.getSubscriptions().size(), 1);
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveEndDate(), new LocalDate(2013, 8, 10));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingEndDate(), new LocalDate(2013, 8, 10));

        secondbundle = bundles3.get(1);
        assertEquals(secondbundle.getSubscriptions().size(), 1);
        assertEquals(secondbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 11));
        assertEquals(secondbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 11));
        assertEquals(secondbundle.getSubscriptions().get(0).getEffectiveEndDate(), new LocalDate(2013, 8, 14));
        assertEquals(secondbundle.getSubscriptions().get(0).getBillingEndDate(), new LocalDate(2013, 8, 14));
        assertEquals(secondbundle.getOriginalCreatedDate().compareTo(firstbundle.getCreatedDate()), 0);

        SubscriptionBundle thirdBundle = bundles3.get(2);
        assertEquals(thirdBundle.getSubscriptions().size(), 1);
        assertEquals(thirdBundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 14));
        assertEquals(thirdBundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 14));
        assertNull(thirdBundle.getSubscriptions().get(0).getEffectiveEndDate());
        assertNull(thirdBundle.getSubscriptions().get(0).getBillingEndDate());
        assertEquals(thirdBundle.getOriginalCreatedDate().compareTo(firstbundle.getCreatedDate()), 0);
    }

    @Test(groups = "slow", description = "Test for https://github.com/killbill/killbill/issues/136")
    public void testAuditLogsForEntitlementAndSubscriptionBaseObjects() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        // Create entitlement
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final Entitlement baseEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
        assertListenerStatus();

        // Get the phase event out of the way
        testListener.pushExpectedEvents(NextEvent.PHASE);
        clock.setDay(new LocalDate(2013, 9, 7));
        assertListenerStatus();

        final LocalDate pauseDate = new LocalDate(2013, 9, 17);
        entitlementApi.pause(baseEntitlement.getBundleId(), pauseDate, callContext);

        final LocalDate resumeDate = new LocalDate(2013, 12, 24);
        entitlementApi.resume(baseEntitlement.getBundleId(), resumeDate, callContext);

        final LocalDate cancelDate = new LocalDate(2013, 12, 27);
        baseEntitlement.cancelEntitlementWithDate(cancelDate, true, callContext);

        testListener.pushExpectedEvents(NextEvent.PAUSE, NextEvent.BLOCK, NextEvent.RESUME, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.BLOCK);
        clock.setDay(cancelDate.plusDays(1));
        assertListenerStatus();

        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);
        final List<SubscriptionEvent> transitions = bundle.getTimeline().getSubscriptionEvents();
        assertEquals(transitions.size(), 9);
        checkSubscriptionEventAuditLog(transitions, 0, SubscriptionEventType.START_ENTITLEMENT);
        checkSubscriptionEventAuditLog(transitions, 1, SubscriptionEventType.START_BILLING);
        checkSubscriptionEventAuditLog(transitions, 2, SubscriptionEventType.PHASE);
        checkSubscriptionEventAuditLog(transitions, 3, SubscriptionEventType.PAUSE_ENTITLEMENT);
        checkSubscriptionEventAuditLog(transitions, 4, SubscriptionEventType.PAUSE_BILLING);
        checkSubscriptionEventAuditLog(transitions, 5, SubscriptionEventType.RESUME_ENTITLEMENT);
        checkSubscriptionEventAuditLog(transitions, 6, SubscriptionEventType.RESUME_BILLING);
        checkSubscriptionEventAuditLog(transitions, 7, SubscriptionEventType.STOP_ENTITLEMENT);
        checkSubscriptionEventAuditLog(transitions, 8, SubscriptionEventType.STOP_BILLING);
    }

    private void checkSubscriptionEventAuditLog(final List<SubscriptionEvent> transitions, final int idx, final SubscriptionEventType expectedType) {
        assertEquals(transitions.get(idx).getSubscriptionEventType(), expectedType);
        final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(transitions.get(idx).getId(), transitions.get(idx).getSubscriptionEventType().getObjectType(), AuditLevel.FULL, callContext);
        assertEquals(auditLogs.size(), 1);
        assertEquals(auditLogs.get(0).getChangeType(), ChangeType.INSERT);
    }
}
