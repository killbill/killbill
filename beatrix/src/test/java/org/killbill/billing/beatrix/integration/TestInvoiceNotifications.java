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

package org.killbill.billing.beatrix.integration;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class TestInvoiceNotifications extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource() {
        ImmutableMap additionalProperties = new ImmutableMap.Builder()
                .put("org.killbill.invoice.dryRunNotificationSchedule", "7d")
                .build();
        return getConfigSource("/beatrix.properties", additionalProperties);
    }

    @Test(groups = "slow")
    public void testInvoiceNotificationBasic() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Move to end of trial =>  2012, 4, 24
        addDaysAndCheckForCompletion(23, NextEvent.INVOICE_NOTIFICATION);

        // Move to end of trial =>  2012, 5, 1
        addDaysAndCheckForCompletion(7, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // Next invoice is scheduled for 2012, 6, 1 so we should have a NOTIFICATION event 7 days before, on 2012, 5, 25
        addDaysAndCheckForCompletion(24, NextEvent.INVOICE_NOTIFICATION);

        // And then verify the invoice is correctly generated
        addDaysAndCheckForCompletion(7, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
    }
}
