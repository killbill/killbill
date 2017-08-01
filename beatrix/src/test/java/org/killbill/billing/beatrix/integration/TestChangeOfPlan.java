/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.beatrix.integration;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestChangeOfPlan extends TestIntegrationBase {


    @Test(groups = "slow")
    public void testChangeOfPlan() throws Exception {
//        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("simplified.xml").toExternalForm(), StandaloneCatalog.class);

        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 8, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement1 = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "FREE", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement1);

        changeEntitlementAndCheckForCompletion(bpEntitlement1, "PROFESSIONAL_PLAN", BillingPeriod.MONTHLY, null, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        changeEntitlementAndCheckForCompletion(bpEntitlement1, "TEAM3S", BillingPeriod.MONTHLY, null, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.TAG);

        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext).size(), 3);
    }

}
