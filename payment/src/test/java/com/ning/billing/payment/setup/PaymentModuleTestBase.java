package com.ning.billing.payment.setup;

import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.invoice.InvoiceListener;
import com.ning.billing.invoice.api.DefaultInvoiceService;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.api.invoice.DefaultInvoicePaymentApi;
import com.ning.billing.invoice.api.user.DefaultInvoiceUserApi;
import com.ning.billing.invoice.dao.DefaultInvoiceDao;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.DefaultInvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.notification.DefaultNextBillingDateNotifier;
import com.ning.billing.invoice.notification.NextBillingDateNotifier;
import org.skife.config.ConfigurationObjectFactory;

import java.util.Properties;

public abstract class PaymentModuleTestBase extends PaymentModule {
    public PaymentModuleTestBase() {
        super();
    }

    public PaymentModuleTestBase(Properties properties) {
        super(properties);
    }

    @Override
    protected void configure() {
        bind(InvoiceService.class).to(DefaultInvoiceService.class).asEagerSingleton();
        bind(NextBillingDateNotifier.class).to(DefaultNextBillingDateNotifier.class).asEagerSingleton();
        bind(InvoiceListener.class).asEagerSingleton();
        bind(InvoiceGenerator.class).to(DefaultInvoiceGenerator.class).asEagerSingleton();
        final InvoiceConfig config = new ConfigurationObjectFactory(System.getProperties()).build(InvoiceConfig.class);
        bind(InvoiceConfig.class).toInstance(config);
        bind(InvoiceDao.class).to(DefaultInvoiceDao.class).asEagerSingleton();
        bind(InvoiceUserApi.class).to(DefaultInvoiceUserApi.class).asEagerSingleton();
        bind(InvoicePaymentApi.class).to(DefaultInvoicePaymentApi.class).asEagerSingleton();

        super.configure();
    }
}
