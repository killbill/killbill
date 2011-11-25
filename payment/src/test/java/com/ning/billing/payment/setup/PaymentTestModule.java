package com.ning.billing.payment.setup;

import com.ning.billing.payment.provider.MockPaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPlugin;
import com.ning.billing.util.eventbus.IEventBus;
import com.ning.billing.util.eventbus.MemoryEventBus;

public class PaymentTestModule extends PaymentModule {
    @Override
    protected void installPaymentProviderPlugin(PaymentConfig config) {
        bind(PaymentProviderPlugin.class).to(MockPaymentProviderPlugin.class);
        bind(MockPaymentProviderPlugin.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        super.configure();
        bind(IEventBus.class).to(MemoryEventBus.class).asEagerSingleton();
    }
}
