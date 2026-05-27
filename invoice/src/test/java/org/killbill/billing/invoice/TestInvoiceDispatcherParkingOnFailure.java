/*
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.invoice;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.invoice.TestInvoiceHelper.DryRunFutureDateArguments;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

// Regression test for issue #2208: when invoice generation fails with a recoverable-looking
// API exception (Catalog/Account/SubscriptionBase) outside of dryRun, the account is parked
// so the operator can find it instead of seeing a silent WARN log.
public class TestInvoiceDispatcherParkingOnFailure extends InvoiceTestSuiteWithEmbeddedDB {

    private Account account;
    private SubscriptionBase subscription;
    private InternalCallContext context;
    private InvoiceDispatcher dispatcher;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();
        account = invoiceUtil.createAccount(callContext);
        subscription = invoiceUtil.createSubscription();
        context = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

        dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                           internalCallContextFactory, invoicePluginDispatcher, locker, bus,
                                           notificationQueueService, invoiceConfig, clock, invoiceOptimizer, parkedAccountsManager);
    }

    // CatalogApiException raised while fetching billing events parks the account in non dry-run mode.
    @Test(groups = "slow")
    public void testCatalogApiExceptionParksAccountInNonDryRun() throws Exception {
        final UUID accountId = account.getId();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(1);
        final LocalDate target = internalCallContext.toLocalDate(effectiveDate);

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(
                Mockito.<UUID>any(),
                Mockito.<DryRunArguments>any(),
                Mockito.<LocalDate>any(),
                Mockito.<InternalCallContext>any()))
               .thenThrow(new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, "missing-plan"));

        Assert.assertTrue(tagUserApi.getTagsForAccount(accountId, true, callContext).isEmpty());

        final List<Invoice> result = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, null, false, context);
        Assert.assertTrue(result.isEmpty());

        final List<Tag> tags = tagUserApi.getTagsForAccount(accountId, false, callContext);
        Assert.assertEquals(tags.size(), 1, "Account should have been parked after CatalogApiException");
        Assert.assertEquals(tags.get(0).getTagDefinitionId(), SystemTags.PARK_TAG_DEFINITION_ID);
    }

    // Dry-run path remains side-effect-free even when the same failure occurs.
    @Test(groups = "slow")
    public void testCatalogApiExceptionDoesNotParkAccountInDryRun() throws Exception {
        final UUID accountId = account.getId();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(1);
        final LocalDate target = internalCallContext.toLocalDate(effectiveDate);

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(
                Mockito.<UUID>any(),
                Mockito.<DryRunArguments>any(),
                Mockito.<LocalDate>any(),
                Mockito.<InternalCallContext>any()))
               .thenThrow(new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, "missing-plan"));

        Assert.assertTrue(tagUserApi.getTagsForAccount(accountId, true, callContext).isEmpty());

        final List<Invoice> result = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, new DryRunFutureDateArguments(), false, context);
        Assert.assertTrue(result.isEmpty());

        // Dry-run must NOT have side effects on tags.
        Assert.assertTrue(tagUserApi.getTagsForAccount(accountId, true, callContext).isEmpty(),
                          "Account must not be parked from a dry-run failure");
    }

    // AccountApiException also triggers parking on the non dry-run path.
    @Test(groups = "slow")
    public void testAccountApiExceptionParksAccountInNonDryRun() throws Exception {
        final UUID accountId = account.getId();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(1);
        final LocalDate target = internalCallContext.toLocalDate(effectiveDate);

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(
                Mockito.<UUID>any(),
                Mockito.<DryRunArguments>any(),
                Mockito.<LocalDate>any(),
                Mockito.<InternalCallContext>any()))
               .thenThrow(new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId));

        Assert.assertTrue(tagUserApi.getTagsForAccount(accountId, true, callContext).isEmpty());

        final List<Invoice> result = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, null, false, context);
        Assert.assertTrue(result.isEmpty());

        final List<Tag> tags = tagUserApi.getTagsForAccount(accountId, false, callContext);
        Assert.assertEquals(tags.size(), 1, "Account should have been parked after AccountApiException");
        Assert.assertEquals(tags.get(0).getTagDefinitionId(), SystemTags.PARK_TAG_DEFINITION_ID);
    }

    // SubscriptionBaseApiException also triggers parking on the non dry-run path.
    @Test(groups = "slow")
    public void testSubscriptionBaseApiExceptionParksAccountInNonDryRun() throws Exception {
        final UUID accountId = account.getId();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(1);
        final LocalDate target = internalCallContext.toLocalDate(effectiveDate);

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(
                Mockito.<UUID>any(),
                Mockito.<DryRunArguments>any(),
                Mockito.<LocalDate>any(),
                Mockito.<InternalCallContext>any()))
               .thenThrow(new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_DATE, "any-id", "any-date"));

        Assert.assertTrue(tagUserApi.getTagsForAccount(accountId, true, callContext).isEmpty());

        final List<Invoice> result = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, null, false, context);
        Assert.assertTrue(result.isEmpty());

        final List<Tag> tags = tagUserApi.getTagsForAccount(accountId, false, callContext);
        Assert.assertEquals(tags.size(), 1, "Account should have been parked after SubscriptionBaseApiException");
        Assert.assertEquals(tags.get(0).getTagDefinitionId(), SystemTags.PARK_TAG_DEFINITION_ID);
    }
}
