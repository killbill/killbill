package com.ning.billing.entitlement.glue;

import com.ning.billing.GuicyKillbillTestWithEmbeddedDBModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.TagStoreModule;
import org.skife.config.ConfigSource;

public class TestEntitlementModuleWithEmbeddedDB extends TestEntitlementModule {

    public TestEntitlementModuleWithEmbeddedDB(final ConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();
        install(new GuicyKillbillTestWithEmbeddedDBModule());
        install(new NonEntityDaoModule());
        install(new BusModule(configSource));
        install(new TagStoreModule());
    }
}
