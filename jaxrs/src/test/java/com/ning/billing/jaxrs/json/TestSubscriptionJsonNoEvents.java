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

package com.ning.billing.jaxrs.json;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestSubscriptionJsonNoEvents extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String accountId = UUID.randomUUID().toString();
        final String entitlementId = UUID.randomUUID().toString();
        final String bundleId = UUID.randomUUID().toString();
        final String externalKey = "ecternalKey";
        final LocalDate startDate = new LocalDate();
        final LocalDate cancelDate = new LocalDate();
        final LocalDate billingStartDate = new LocalDate();
        final LocalDate billingEndDate = new LocalDate();
        final String productName = UUID.randomUUID().toString();
        final String productCategory = UUID.randomUUID().toString();
        final String billingPeriod = UUID.randomUUID().toString();
        final String priceList = UUID.randomUUID().toString();
        final LocalDate chargedThroughDate = new LocalDate();
        final DateTime endDate = new DateTime(DateTimeZone.UTC);
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final SubscriptionJsonNoEvents subscriptionJsonNoEvents = new SubscriptionJsonNoEvents(accountId, bundleId, entitlementId, externalKey, startDate, productName,
                                                                                               productCategory, billingPeriod, priceList, cancelDate, auditLogs, chargedThroughDate,
                                                                                               billingStartDate, billingEndDate, new Integer(1));

        Assert.assertEquals(subscriptionJsonNoEvents.getEntitlementId(), entitlementId);
        Assert.assertEquals(subscriptionJsonNoEvents.getBundleId(), bundleId);
        Assert.assertEquals(subscriptionJsonNoEvents.getStartDate(), startDate);
        Assert.assertEquals(subscriptionJsonNoEvents.getProductName(), productName);
        Assert.assertEquals(subscriptionJsonNoEvents.getProductCategory(), productCategory);
        Assert.assertEquals(subscriptionJsonNoEvents.getBillingPeriod(), billingPeriod);
        Assert.assertEquals(subscriptionJsonNoEvents.getPriceList(), priceList);
        Assert.assertEquals(subscriptionJsonNoEvents.getChargedThroughDate(), chargedThroughDate);
        Assert.assertEquals(subscriptionJsonNoEvents.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(subscriptionJsonNoEvents);

        final SubscriptionJsonNoEvents fromJson = mapper.readValue(asJson, SubscriptionJsonNoEvents.class);
        Assert.assertEquals(fromJson, subscriptionJsonNoEvents);
    }
}
