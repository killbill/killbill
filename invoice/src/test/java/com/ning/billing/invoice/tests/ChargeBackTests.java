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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.invoice.DefaultInvoicePaymentApi;
import com.ning.billing.invoice.dao.DefaultInvoiceDao;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.InvoiceSqlDao;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.notification.MockNextBillingDatePoster;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.tag.dao.MockTagDao;
import com.ning.billing.util.tag.dao.TagDao;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(groups = {"slow", "invoicing"})
public class ChargeBackTests {
    private final static BigDecimal FIFTEEN = new BigDecimal("15.00");
    private final static BigDecimal THIRTY = new BigDecimal("30.00");
    private final static BigDecimal ONE_MILLION = new BigDecimal("1000000.00");
    private InvoiceSqlDao invoiceSqlDao;
    private InvoicePaymentApi invoicePaymentApi;
    private CallContext context;
    private final Clock clock = new ClockMock();
    private final static Currency CURRENCY = Currency.EUR;

    @BeforeClass
    public void setup() {
        MysqlTestingHelper helper = new MysqlTestingHelper();
        IDBI dbi = helper.getDBI();
        invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        invoiceSqlDao.test();

        NextBillingDatePoster nextBillingDatePoster = new MockNextBillingDatePoster();
        TagDao tagDao = new MockTagDao();
        InvoiceDao invoiceDao = new DefaultInvoiceDao(dbi, nextBillingDatePoster, tagDao);
        invoicePaymentApi = new DefaultInvoicePaymentApi(invoiceDao);

        context = new TestCallContext("Charge back tests");
    }

    @Test
    public void testCompleteChargeBack() throws InvoiceApiException {
        Invoice invoice = createAndPersistInvoice(THIRTY);
        InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a full charge back
        invoicePaymentApi.processChargeback(payment.getId(), THIRTY, context);

        // check amount owed
        BigDecimal amount = invoicePaymentApi.getRemainingAmountPaid(payment.getId());
        assertTrue(amount.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    public void testPartialChargeBack() throws InvoiceApiException {
        Invoice invoice = createAndPersistInvoice(THIRTY);
        InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a partial charge back
        invoicePaymentApi.processChargeback(payment.getId(), FIFTEEN, context);

        // check amount owed
        BigDecimal amount = invoicePaymentApi.getRemainingAmountPaid(payment.getId());
        assertTrue(amount.compareTo(FIFTEEN) == 0);
    }

    @Test(expectedExceptions = InvoiceApiException.class)
    public void testChargeBackLargerThanPaymentAmount() throws InvoiceApiException {
        Invoice invoice = createAndPersistInvoice(THIRTY);
        InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a large charge back
        invoicePaymentApi.processChargeback(payment.getId(), ONE_MILLION, context);
    }

    @Test(expectedExceptions = InvoiceApiException.class)
    public void testNegativeChargeBackAmount() throws InvoiceApiException {
        Invoice invoice = createAndPersistInvoice(THIRTY);
        InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a partial charge back
        invoicePaymentApi.processChargeback(payment.getId(), BigDecimal.ONE.negate(), context);
    }

    @Test
    public void testGetAccountIdFromPaymentIdHappyPath() throws InvoiceApiException {
        Invoice invoice = createAndPersistInvoice(THIRTY);
        InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);
        UUID accountId = invoicePaymentApi.getAccountIdFromInvoicePaymentId(payment.getId());
        assertEquals(accountId, invoice.getAccountId());
    }

    @Test(expectedExceptions = InvoiceApiException.class)
    public void testGetAccountIdFromPaymentIdBadPaymentId() throws InvoiceApiException {
        invoicePaymentApi.getAccountIdFromInvoicePaymentId(UUID.randomUUID());
    }

    @Test
    public void testGetChargeBacksByAccountIdWithEmptyReturnSet() throws InvoiceApiException {
        List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByAccountId(UUID.randomUUID());
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 0);
    }

    @Test
    public void testGetChargeBacksByAccountIdHappyPath() throws InvoiceApiException {
        Invoice invoice = createAndPersistInvoice(THIRTY);
        InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a partial charge back
        invoicePaymentApi.processChargeback(payment.getId(), FIFTEEN, context);

        List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByAccountId(invoice.getAccountId());
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 1);
        assertEquals(chargebacks.get(0).getReversedInvoicePaymentId(), payment.getId());
    }

    @Test
    public void testGetChargeBacksByInvoicePaymentIdWithEmptyReturnSet() throws InvoiceApiException {
        List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByInvoicePaymentId(UUID.randomUUID());
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 0);
    }

    @Test
    public void testGetChargeBacksByInvoicePaymentIdHappyPath() throws InvoiceApiException {
        Invoice invoice = createAndPersistInvoice(THIRTY);
        InvoicePayment payment = createAndPersistPayment(invoice.getId(), THIRTY);

        // create a partial charge back
        invoicePaymentApi.processChargeback(payment.getId(), FIFTEEN, context);

        List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByInvoicePaymentId(payment.getId());
        assertNotNull(chargebacks);
        assertEquals(chargebacks.size(), 1);
        assertEquals(chargebacks.get(0).getReversedInvoicePaymentId(), payment.getId());
    }

    private Invoice createAndPersistInvoice(BigDecimal amount) {
        Invoice invoice = BrainDeadProxyFactory.createBrainDeadProxyFor(Invoice.class);
        UUID invoiceId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ZombieControl zombie = (ZombieControl) invoice;
        zombie.addResult("getId", invoiceId);
        zombie.addResult("getAccountId", accountId);
        zombie.addResult("getInvoiceDate", clock.getUTCNow());
        zombie.addResult("getTargetDate", clock.getUTCNow());
        zombie.addResult("getCurrency", CURRENCY);
        zombie.addResult("isMigrationInvoice", false);

        List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        items.add(createInvoiceItem(invoiceId, accountId, amount));
        zombie.addResult("getInvoiceItems", items);

        invoiceSqlDao.create(invoice, context);

        return invoice;
    }

    private InvoiceItem createInvoiceItem(UUID invoiceId, UUID accountId, BigDecimal amount) {
        return new FixedPriceInvoiceItem(invoiceId, accountId, UUID.randomUUID(), UUID.randomUUID(),
                "charge back test", "charge back phase", clock.getUTCNow(), clock.getUTCNow(), amount, CURRENCY);
    }

    private InvoicePayment createAndPersistPayment(UUID invoiceId, BigDecimal amount) {
        InvoicePayment payment = BrainDeadProxyFactory.createBrainDeadProxyFor(InvoicePayment.class);
        ZombieControl zombie = (ZombieControl) payment;
        zombie.addResult("getId", UUID.randomUUID());
        zombie.addResult("getInvoiceId", invoiceId);
        zombie.addResult("getPaymentAttemptId", UUID.randomUUID());
        zombie.addResult("getPaymentAttemptDate", clock.getUTCNow());
        zombie.addResult("getAmount", amount);
        zombie.addResult("getCurrency", CURRENCY);
        zombie.addResult("getReversedInvoicePaymentId", BrainDeadProxyFactory.ZOMBIE_VOID);

        invoicePaymentApi.notifyOfPaymentAttempt(payment, context);

        return payment;
    }
}
