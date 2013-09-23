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

package com.ning.billing.invoice.api.migration;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.dao.InvoiceModelDao;
import com.ning.billing.invoice.dao.InvoiceModelDaoHelper;

public class TestDefaultInvoiceMigrationApi extends InvoiceTestSuiteWithEmbeddedDB {

    private final Logger log = LoggerFactory.getLogger(TestDefaultInvoiceMigrationApi.class);

    private LocalDate date_migrated;
    private DateTime date_regular;

    private UUID accountId;
    private UUID migrationInvoiceId;
    private UUID regularInvoiceId;

    private static final BigDecimal MIGRATION_INVOICE_AMOUNT = new BigDecimal("100.00");
    private static final Currency MIGRATION_INVOICE_CURRENCY = Currency.USD;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        date_migrated = clock.getUTCToday().minusYears(1);
        date_regular = clock.getUTCNow();

        final Account account = invoiceUtil.createAccount(callContext);
        accountId = account.getId();
        migrationInvoiceId = createAndCheckMigrationInvoice(accountId);
        regularInvoiceId = invoiceUtil.generateRegularInvoice(account, date_regular, callContext);
    }

    private UUID createAndCheckMigrationInvoice(final UUID accountId) throws InvoiceApiException {
        final UUID migrationInvoiceId = migrationApi.createMigrationInvoice(accountId, date_migrated, MIGRATION_INVOICE_AMOUNT,
                                                                            MIGRATION_INVOICE_CURRENCY, callContext);
        Assert.assertNotNull(migrationInvoiceId);
        //Double check it was created and values are correct

        final InvoiceModelDao invoice = invoiceDao.getById(migrationInvoiceId, internalCallContext);
        Assert.assertNotNull(invoice);

        Assert.assertEquals(invoice.getAccountId(), accountId);
        Assert.assertEquals(invoice.getTargetDate().compareTo(date_migrated), 0); //temp to avoid tz test artifact
        //		Assert.assertEquals(invoice.getTargetDate(),now);
        Assert.assertEquals(invoice.getInvoiceItems().size(), 1);
        Assert.assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(MIGRATION_INVOICE_AMOUNT), 0);
        Assert.assertEquals(InvoiceModelDaoHelper.getBalance(invoice).compareTo(MIGRATION_INVOICE_AMOUNT), 0);
        Assert.assertEquals(invoice.getCurrency(), MIGRATION_INVOICE_CURRENCY);
        Assert.assertTrue(invoice.isMigrated());

        return migrationInvoiceId;
    }

    @Test(groups = "slow")
    public void testUserApiAccess() {
        final List<Invoice> byAccount = invoiceUserApi.getInvoicesByAccount(accountId, callContext);
        Assert.assertEquals(byAccount.size(), 1);
        Assert.assertEquals(byAccount.get(0).getId(), regularInvoiceId);

        final List<Invoice> byAccountAndDate = invoiceUserApi.getInvoicesByAccount(accountId, date_migrated.minusDays(1), callContext);
        Assert.assertEquals(byAccountAndDate.size(), 1);
        Assert.assertEquals(byAccountAndDate.get(0).getId(), regularInvoiceId);

        final Collection<Invoice> unpaid = invoiceUserApi.getUnpaidInvoicesByAccountId(accountId, new LocalDate(date_regular.plusDays(1)), callContext);
        Assert.assertEquals(unpaid.size(), 2);
    }

    // Check migration invoice IS returned for payment api calls
    @Test(groups = "slow")
    public void testPaymentApi() {
        final List<Invoice> allByAccount = invoicePaymentApi.getAllInvoicesByAccount(accountId, callContext);
        Assert.assertEquals(allByAccount.size(), 2);
        Assert.assertTrue(checkContains(allByAccount, regularInvoiceId));
        Assert.assertTrue(checkContains(allByAccount, migrationInvoiceId));
    }

    // ACCOUNT balance should reflect total of migration and non-migration invoices
    @Test(groups = "slow")
    public void testBalance() throws InvoiceApiException {
        final InvoiceModelDao migrationInvoice = invoiceDao.getById(migrationInvoiceId, internalCallContext);
        final InvoiceModelDao regularInvoice = invoiceDao.getById(regularInvoiceId, internalCallContext);
        final BigDecimal balanceOfAllInvoices = InvoiceModelDaoHelper.getBalance(migrationInvoice).add(InvoiceModelDaoHelper.getBalance(regularInvoice));

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        log.info("ACCOUNT balance: " + accountBalance + " should equal the Balance Of All Invoices: " + balanceOfAllInvoices);
        Assert.assertEquals(accountBalance.compareTo(balanceOfAllInvoices), 0);
    }

    private boolean checkContains(final List<Invoice> invoices, final UUID invoiceId) {
        for (final Invoice invoice : invoices) {
            if (invoice.getId().equals(invoiceId)) {
                return true;
            }
        }
        return false;
    }
}
