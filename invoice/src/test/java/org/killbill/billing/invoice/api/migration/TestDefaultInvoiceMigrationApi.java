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

package org.killbill.billing.invoice.api.migration;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDaoHelper;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDefaultInvoiceMigrationApi extends InvoiceTestSuiteWithEmbeddedDB {

    private LocalDate date_migrated;
    private LocalDate date_regular;

    private UUID accountId;
    private UUID migrationInvoiceId;
    private UUID regularInvoiceId;

    private static final BigDecimal MIGRATION_INVOICE_AMOUNT = new BigDecimal("100.00");
    private static final Currency MIGRATION_INVOICE_CURRENCY = Currency.USD;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        date_migrated = clock.getUTCToday().minusYears(1);
        date_regular = clock.getUTCToday();

        final Account account = invoiceUtil.createAccount(callContext);
        accountId = account.getId();
        migrationInvoiceId = createAndCheckMigrationInvoice(accountId);
        regularInvoiceId = invoiceUtil.generateRegularInvoice(account, date_regular, callContext);
    }

    private UUID createAndCheckMigrationInvoice(final UUID accountId) throws InvoiceApiException {

        final InvoiceItem recurringItem = new RecurringInvoiceItem(null, accountId, null, null, "productName", "planName", "phaseName", new LocalDate(2016, 2, 1), new LocalDate(2016, 3, 1), MIGRATION_INVOICE_AMOUNT, MIGRATION_INVOICE_AMOUNT, MIGRATION_INVOICE_CURRENCY);
        final UUID migrationInvoiceId = invoiceUserApi.createMigrationInvoice(accountId, date_migrated, ImmutableList.of(recurringItem), callContext);
        Assert.assertNotNull(migrationInvoiceId);
        //Double check it was created and values are correct

        final InvoiceModelDao invoice = invoiceDao.getById(migrationInvoiceId, internalCallContext);
        Assert.assertNotNull(invoice);

        Assert.assertEquals(invoice.getAccountId(), accountId);
        Assert.assertEquals(invoice.getTargetDate().compareTo(date_migrated), 0); //temp to avoid tz test artifact
        //		Assert.assertEquals(invoice.getTargetDate(),now);
        Assert.assertEquals(invoice.getInvoiceItems().size(), 1);
        Assert.assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(MIGRATION_INVOICE_AMOUNT), 0);
        Assert.assertEquals(invoice.getInvoiceItems().get(0).getType(), InvoiceItemType.RECURRING);
        Assert.assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice).compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(invoice.getCurrency(), MIGRATION_INVOICE_CURRENCY);
        Assert.assertTrue(invoice.isMigrated());

        return migrationInvoiceId;
    }

    @Test(groups = "slow")
    public void testUserApiAccess() {
        final List<Invoice> byAccount = invoiceUserApi.getInvoicesByAccount(accountId, false, false, callContext);
        Assert.assertEquals(byAccount.size(), 1);
        Assert.assertEquals(byAccount.get(0).getId(), regularInvoiceId);

        final List<Invoice> byAccountAndDate = invoiceUserApi.getInvoicesByAccount(accountId, date_migrated.minusDays(1), false, callContext);
        Assert.assertEquals(byAccountAndDate.size(), 1);
        Assert.assertEquals(byAccountAndDate.get(0).getId(), regularInvoiceId);

        final Collection<Invoice> unpaid = invoiceUserApi.getUnpaidInvoicesByAccountId(accountId, new LocalDate(date_regular.plusDays(1)), callContext);
        Assert.assertEquals(unpaid.size(), 1);
        Assert.assertEquals(unpaid.iterator().next().getId(), regularInvoiceId);
    }

    // ACCOUNT balance should reflect total of migration and non-migration invoices
    @Test(groups = "slow")
    public void testBalance() throws InvoiceApiException {
        final InvoiceModelDao migrationInvoice = invoiceDao.getById(migrationInvoiceId, internalCallContext);
        final InvoiceModelDao regularInvoice = invoiceDao.getById(regularInvoiceId, internalCallContext);
        final BigDecimal balanceOfAllInvoices = InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(migrationInvoice).add(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(regularInvoice));

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance.compareTo(balanceOfAllInvoices), 0);
    }
}
