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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
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
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.invoice.DefaultInvoicePaymentApi;
import com.ning.billing.invoice.dao.DefaultInvoiceDao;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.InvoiceSqlDao;
import com.ning.billing.invoice.glue.InvoiceModuleWithEmbeddedDb;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.notification.MockNextBillingDatePoster;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.tag.api.DefaultTagUserApi;
import com.ning.billing.util.tag.dao.MockTagDao;
import com.ning.billing.util.tag.dao.MockTagDefinitionDao;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestChargeBacks extends InvoiceTestSuiteWithEmbeddedDB {
    private static final BigDecimal FIFTEEN = new BigDecimal("15.00");
    private static final BigDecimal THIRTY = new BigDecimal("30.00");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000.00");
    private InvoiceSqlDao invoiceSqlDao;
    private InvoicePaymentApi invoicePaymentApi;
    private CallContext context;
    private final Clock clock = new ClockMock();
    private static final Currency CURRENCY = Currency.EUR;

    @BeforeSuite(groups = "slow")
    public void setup() throws IOException {
        loadSystemPropertiesFromClasspath("/resource.properties");

        final MysqlTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getMysqlTestingHelper();
        final IDBI dbi = helper.getDBI();

        invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        invoiceSqlDao.test();

        final NextBillingDatePoster nextBillingDatePoster = new MockNextBillingDatePoster();
        final TagDefinitionDao tagDefinitionDao = new MockTagDefinitionDao();
        final TagDao tagDao = new MockTagDao();
        final TagUserApi tagUserApi = new DefaultTagUserApi(tagDefinitionDao, tagDao);
        final InvoiceDao invoiceDao = new DefaultInvoiceDao(dbi, nextBillingDatePoster, tagUserApi, clock);
        invoicePaymentApi = new DefaultInvoicePaymentApi(invoiceDao);

        context = new TestCallContext("Charge back tests");
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
        final Invoice invoice = createAndPersistInvoice(THIRTY);
        final InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a full charge back
        invoicePaymentApi.createChargeback(payment.getId(), THIRTY, context);

        // check amount owed
        final BigDecimal amount = invoicePaymentApi.getRemainingAmountPaid(payment.getId());
        assertTrue(amount.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test(groups = "slow")
    public void testPartialChargeBack() throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(THIRTY);
        final InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a partial charge back
        invoicePaymentApi.createChargeback(payment.getId(), FIFTEEN, context);

        // check amount owed
        final BigDecimal amount = invoicePaymentApi.getRemainingAmountPaid(payment.getId());
        assertTrue(amount.compareTo(FIFTEEN) == 0);
    }

    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class)
    public void testChargeBackLargerThanPaymentAmount() throws InvoiceApiException {
        try {
            final Invoice invoice = createAndPersistInvoice(THIRTY);
            final InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

            // create a large charge back
            invoicePaymentApi.createChargeback(payment.getId(), ONE_MILLION, context);
            fail("Expected a failure...");
        } catch (TransactionFailedException expected) {
            throw (InvoiceApiException) expected.getCause();
        }
    }

    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class)
    public void testNegativeChargeBackAmount() throws InvoiceApiException {
        try {
            final Invoice invoice = createAndPersistInvoice(THIRTY);
            final InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

            // create a partial charge back
            invoicePaymentApi.createChargeback(payment.getId(), BigDecimal.ONE.negate(), context);
        } catch (TransactionFailedException expected) {
            throw (InvoiceApiException) expected.getCause();
        }
    }

    @Test(groups = "slow")
    public void testGetAccountIdFromPaymentIdHappyPath() throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(THIRTY);
        final InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);
        final UUID accountId = invoicePaymentApi.getAccountIdFromInvoicePaymentId(payment.getId());
        assertEquals(accountId, invoice.getAccountId());
    }

    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class)
    public void testGetAccountIdFromPaymentIdBadPaymentId() throws InvoiceApiException {
        invoicePaymentApi.getAccountIdFromInvoicePaymentId(UUID.randomUUID());
    }

    @Test(groups = "slow")
    public void testGetChargeBacksByAccountIdWithEmptyReturnSet() throws InvoiceApiException {
        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByAccountId(UUID.randomUUID());
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 0);
    }

    @Test(groups = "slow")
    public void testGetChargeBacksByAccountIdHappyPath() throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(THIRTY);
        final InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a partial charge back
        invoicePaymentApi.createChargeback(payment.getId(), FIFTEEN, context);

        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByAccountId(invoice.getAccountId());
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 1);
        assertEquals(chargebacks.get(0).getLinkedInvoicePaymentId(), payment.getId());
    }

    @Test(groups = "slow")
    public void testGetChargeBacksByPaymentIdWithEmptyReturnSet() throws InvoiceApiException {
        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByPaymentId(UUID.randomUUID());
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 0);
    }

    @Test(groups = "slow")
    public void testGetChargeBacksByInvoicePaymentIdHappyPath() throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(THIRTY);
        final InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a partial charge back
        invoicePaymentApi.createChargeback(payment.getId(), FIFTEEN, context);

        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByPaymentId(payment.getPaymentId());
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 1);
        assertEquals(chargebacks.get(0).getLinkedInvoicePaymentId(), payment.getId());
    }

    private Invoice createAndPersistInvoice(final BigDecimal amount) {
        final Invoice invoice = Mockito.mock(Invoice.class);
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();

        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getTargetDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getCurrency()).thenReturn(CURRENCY);
        Mockito.when(invoice.isMigrationInvoice()).thenReturn(false);

        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        items.add(createInvoiceItem(invoiceId, accountId, amount));
        Mockito.when(invoice.getInvoiceItems()).thenReturn(items);

        invoiceSqlDao.create(invoice, context);

        return invoice;
    }

    private InvoiceItem createInvoiceItem(final UUID invoiceId, final UUID accountId, final BigDecimal amount) {
        return new FixedPriceInvoiceItem(invoiceId, accountId, UUID.randomUUID(), UUID.randomUUID(),
                                         "charge back test", "charge back phase", clock.getUTCToday(), amount, CURRENCY);
    }

    private InvoicePayment createAndPersistPayment(final UUID invoiceId, final BigDecimal amount) {
        final InvoicePayment payment = Mockito.mock(InvoicePayment.class);
        Mockito.when(payment.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getType()).thenReturn(InvoicePaymentType.ATTEMPT);
        Mockito.when(payment.getInvoiceId()).thenReturn(invoiceId);
        Mockito.when(payment.getPaymentId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getPaymentDate()).thenReturn(clock.getUTCNow());
        Mockito.when(payment.getAmount()).thenReturn(amount);
        Mockito.when(payment.getCurrency()).thenReturn(CURRENCY);

        invoicePaymentApi.notifyOfPayment(payment, context);

        return payment;
    }
}
