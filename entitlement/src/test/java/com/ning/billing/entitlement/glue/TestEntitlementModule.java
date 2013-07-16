package com.ning.billing.entitlement.glue;

import com.ning.billing.catalog.MockCatalogModule;
import com.ning.billing.mock.glue.MockAccountModule;
import com.ning.billing.mock.glue.MockSubscriptionModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import org.skife.config.ConfigSource;

public class TestEntitlementModule extends DefaultEntitlementModule {

    final protected ConfigSource configSource;

    public TestEntitlementModule(final ConfigSource configSource) {
        super(configSource);
        this.configSource = configSource;
    }

    @Override
    protected void configure() {
        super.configure();
        install(new CacheModule(configSource));
        install(new CallContextModule());
        install(new MockAccountModule());
        install(new MockCatalogModule());
        install(new MockSubscriptionModule());

    }
}
