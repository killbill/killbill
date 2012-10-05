/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.invoice.tests;

import static com.ning.billing.invoice.tests.InvoiceTestUtils.createAndPersistInvoice;
import static com.ning.billing.invoice.tests.InvoiceTestUtils.createAndPersistPayment;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.invoice.DefaultInvoicePaymentApi;
import com.ning.billing.invoice.dao.AuditedInvoiceDao;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.InvoiceItemSqlDao;
import com.ning.billing.invoice.dao.InvoiceSqlDao;
import com.ning.billing.invoice.glue.InvoiceModuleWithEmbeddedDb;
import com.ning.billing.invoice.notification.MockNextBillingDatePoster;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestChargeBacks extends InvoiceTestSuiteWithEmbeddedDB {

    private static final BigDecimal FIFTEEN = new BigDecimal("15.00");
    private static final BigDecimal THIRTY = new BigDecimal("30.00");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000.00");
    private InvoiceSqlDao invoiceSqlDao;
    private InvoiceItemSqlDao invoiceItemSqlDao;
    private InvoicePaymentApi invoicePaymentApi;
    private InternalCallContextFactory internalCallContextFactory;

    private final Clock clock = new ClockMock();
    private static final Currency CURRENCY = Currency.EUR;

    @BeforeSuite(groups = "slow")
    public void setup() throws IOException {
        loadSystemPropertiesFromClasspath("/resource.properties");

        final MysqlTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getMysqlTestingHelper();
        final IDBI dbi = helper.getDBI();

        invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        invoiceSqlDao.test(internalCallContext);

        invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        invoiceItemSqlDao.test(internalCallContext);
        final NextBillingDatePoster nextBillingDatePoster = new MockNextBillingDatePoster();
        internalCallContextFactory = new InternalCallContextFactory(dbi, clock);
        final InvoiceDao invoiceDao = new AuditedInvoiceDao(dbi, nextBillingDatePoster, clock, Mockito.mock(Bus.class));
        invoicePaymentApi = new DefaultInvoicePaymentApi(invoiceDao, internalCallContextFactory);
    }

    private static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = InvoiceModuleWithEmbeddedDb.class.getResource(resource);
        assertNotNull(url);
        try {
            System.getProperties().load(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test(groups = "slow")
    public void testCompleteChargeBack() throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock, THIRTY, CURRENCY, callContext, internalCallContextFactory);
        final InvoicePayment payment = createAndPersistPayment(invoicePaymentApi, clock, invoice.getId(), THIRTY, CURRENCY, callContext);

        // create a full charge back
        invoicePaymentApi.createChargeback(payment.getId(), THIRTY, callContext);

        // check amount owed
        final BigDecimal amount = invoicePaymentApi.getRemainingAmountPaid(payment.getId(), callContext);
        assertTrue(amount.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test(groups = "slow")
    public void testPartialChargeBack() throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock, THIRTY, CURRENCY, callContext, internalCallContextFactory);
        final InvoicePayment payment = createAndPersistPayment(invoicePaymentApi, clock, invoice.getId(), THIRTY, CURRENCY, callContext);

        // create a partial charge back
        invoicePaymentApi.createChargeback(payment.getId(), FIFTEEN, callContext);

        // check amount owed
        final BigDecimal amount = invoicePaymentApi.getRemainingAmountPaid(payment.getId(), callContext);
        assertTrue(amount.compareTo(FIFTEEN) == 0);
    }

    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class)
    public void testChargeBackLargerThanPaymentAmount() throws InvoiceApiException {
        try {
            final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock, THIRTY, CURRENCY, callContext, internalCallContextFactory);
            final InvoicePayment payment = createAndPersistPayment(invoicePaymentApi, clock, invoice.getId(), THIRTY, CURRENCY, callContext);

            // create a large charge back
            invoicePaymentApi.createChargeback(payment.getId(), ONE_MILLION, callContext);
            fail("Expected a failure...");
        } catch (TransactionFailedException expected) {
            throw (InvoiceApiException) expected.getCause();
        }
    }

    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class)
    public void testNegativeChargeBackAmount() throws InvoiceApiException {
        try {
            final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock, THIRTY, CURRENCY, callContext, internalCallContextFactory);
            final InvoicePayment payment = createAndPersistPayment(invoicePaymentApi, clock, invoice.getId(), THIRTY, CURRENCY, callContext);

            // create a partial charge back
            invoicePaymentApi.createChargeback(payment.getId(), BigDecimal.ONE.negate(), callContext);
        } catch (TransactionFailedException expected) {
            throw (InvoiceApiException) expected.getCause();
        }
    }

    @Test(groups = "slow")
    public void testGetAccountIdFromPaymentIdHappyPath() throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock, THIRTY, CURRENCY, callContext, internalCallContextFactory);
        final InvoicePayment payment = createAndPersistPayment(invoicePaymentApi, clock, invoice.getId(), THIRTY, CURRENCY, callContext);
        final UUID accountId = invoicePaymentApi.getAccountIdFromInvoicePaymentId(payment.getId(), callContext);
        assertEquals(accountId, invoice.getAccountId());
    }

    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class)
    public void testGetAccountIdFromPaymentIdBadPaymentId() throws InvoiceApiException {
        invoicePaymentApi.getAccountIdFromInvoicePaymentId(UUID.randomUUID(), callContext);
    }

    @Test(groups = "slow")
    public void testGetChargeBacksByAccountIdWithEmptyReturnSet() throws InvoiceApiException {
        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByAccountId(UUID.randomUUID(), callContext);
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 0);
    }

    @Test(groups = "slow")
    public void testGetChargeBacksByAccountIdHappyPath() throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock, THIRTY, CURRENCY, callContext, internalCallContextFactory);
        final InvoicePayment payment = createAndPersistPayment(invoicePaymentApi, clock, invoice.getId(), THIRTY, CURRENCY, callContext);

        // create a partial charge back
        invoicePaymentApi.createChargeback(payment.getId(), FIFTEEN, callContext);

        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByAccountId(invoice.getAccountId(), callContext);
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 1);
        assertEquals(chargebacks.get(0).getLinkedInvoicePaymentId(), payment.getId());
    }

    @Test(groups = "slow")
    public void testGetChargeBacksByPaymentIdWithEmptyReturnSet() throws InvoiceApiException {
        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByPaymentId(UUID.randomUUID(), callContext);
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 0);
    }

    @Test(groups = "slow")
    public void testGetChargeBacksByInvoicePaymentIdHappyPath() throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock, THIRTY, CURRENCY, callContext, internalCallContextFactory);
        final InvoicePayment payment = createAndPersistPayment(invoicePaymentApi, clock, invoice.getId(), THIRTY, CURRENCY, callContext);

        // create a partial charge back
        invoicePaymentApi.createChargeback(payment.getId(), FIFTEEN, callContext);

        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByPaymentId(payment.getPaymentId(), callContext);
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 1);
        assertEquals(chargebacks.get(0).getLinkedInvoicePaymentId(), payment.getId());
    }
}
