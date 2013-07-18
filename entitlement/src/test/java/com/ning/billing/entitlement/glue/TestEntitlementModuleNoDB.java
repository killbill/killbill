package com.ning.billing.entitlement.glue;

import com.ning.billing.GuicyKillbillTestNoDBModule;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.dao.MockBlockingStateDao;
import com.ning.billing.mock.glue.MockNonEntityDaoModule;
import com.ning.billing.mock.glue.MockTagModule;
import com.ning.billing.util.bus.InMemoryBusModule;
import org.skife.config.ConfigSource;

public class TestEntitlementModuleNoDB extends TestEntitlementModule {

    public TestEntitlementModuleNoDB(final ConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();
        install(new GuicyKillbillTestNoDBModule());
        install(new MockNonEntityDaoModule());
        install(new InMemoryBusModule(configSource));
        install(new MockTagModule());
    }

    @Override
    public void installBlockingStateDao() {
        bind(BlockingStateDao.class).to(MockBlockingStateDao.class).asEagerSingleton();
    }

}
