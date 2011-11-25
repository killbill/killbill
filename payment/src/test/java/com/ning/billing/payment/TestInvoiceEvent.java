package com.ning.billing.payment;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.model.Invoice;
import com.ning.billing.invoice.model.InvoiceItem;
import com.ning.billing.util.eventbus.IEventBus;
import com.ning.billing.util.eventbus.IEventBus.EventBusException;
import com.ning.billing.util.eventbus.MemoryEventBus;

public class TestInvoiceEvent {
    private static class MockPaymentProcessor {
        private final List<PaymentInfo> processedPayments = Collections.synchronizedList(new ArrayList<PaymentInfo>());

        @Subscribe
        public void processedPayment(PaymentInfo paymentInfo) {
            processedPayments.add(paymentInfo);
        }

        public List<PaymentInfo> getProcessedPayments() {
            return new ArrayList<PaymentInfo>(processedPayments);
        }
    }

    private IEventBus eventBus;
    private InvoiceProcessor invoiceProcessor;
    private MockPaymentProcessor mockPaymentProcessor;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws EventBusException {
        eventBus = new MemoryEventBus();
        eventBus.start();

        invoiceProcessor = new MockInvoiceProcessor(eventBus);
        mockPaymentProcessor = new MockPaymentProcessor();
        eventBus.register(invoiceProcessor);
        eventBus.register(mockPaymentProcessor);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        eventBus.stop();
    }

    @Test
    public void testSimpleInvoice() throws Exception {
        final UUID subscriptionUuid = UUID.randomUUID();
        final UUID invoiceUuid = UUID.randomUUID();
        final DateTime now = new DateTime();
        final InvoiceItem lineItem = new InvoiceItem(invoiceUuid,
                                                     subscriptionUuid,
                                                     now,
                                                     now.plusMonths(1),
                                                     "Test invoice",
                                                     new BigDecimal("10"),
                                                     new BigDecimal("1"),
                                                     Currency.USD);
        final List<InvoiceItem> lineItems = Arrays.asList(lineItem);
        final Invoice invoice = new Invoice(invoiceUuid, lineItems, Currency.USD);

        eventBus.post(invoice);
        await().atMost(1, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                List<PaymentInfo> processedPayments = mockPaymentProcessor.getProcessedPayments();
                return processedPayments.size() == 1;
            }
        });
    }
}
