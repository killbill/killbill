package com.ning.billing.payment;

import static org.testng.Assert.assertNotNull;

import java.util.UUID;

import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.payment.setup.PaymentTestModule;

@Guice(modules = PaymentTestModule.class)
public class TestNotifyInvoicePaymentApi extends TestPaymentProvider {

    @Test
    public void testNotifyPaymentSuccess() {
        final Account account = createTestAccount();
        final Invoice invoice = createTestInvoice(account);

        PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice);
        invoicePaymentApi.paymentSuccessful(invoice.getId(),
                                     invoice.getAmountOutstanding(),
                                     invoice.getCurrency(),
                                     paymentAttempt.getPaymentAttemptId(),
                                     paymentAttempt.getPaymentAttemptDate());

        InvoicePayment invoicePayment = invoicePaymentApi.getInvoicePayment(invoice.getId(), paymentAttempt.getPaymentAttemptId());

        assertNotNull(invoicePayment);
    }

    @Test
    public void testNotifyPaymentFailure() {
        final Account account = createTestAccount();
        final Invoice invoice = createTestInvoice(account);

        PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice);
        invoicePaymentApi.paymentFailed(invoice.getId(),
                                 paymentAttempt.getPaymentAttemptId(),
                                 paymentAttempt.getPaymentAttemptDate());

        InvoicePayment invoicePayment = invoicePaymentApi.getInvoicePayment(invoice.getId(), paymentAttempt.getPaymentAttemptId());

        assertNotNull(invoicePayment);
    }

}
