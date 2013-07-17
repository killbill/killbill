package com.ning.billing.entitlement.glue;

import com.google.inject.AbstractModule;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.entitlement.api.BlockingAccountUserApi;
import com.ning.billing.entitlement.api.BlockingSubscriptionUserApi;
import com.ning.billing.entitlement.api.DefaultEntitlementApi;
import com.ning.billing.entitlement.api.svcs.DefaultInternalBlockingApi;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.DefaultBlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.dao.DefaultBlockingStateDao;
import com.ning.billing.glue.EntitlementModule;
import com.ning.billing.entitlement.api.EntitlementApi;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import org.skife.config.ConfigSource;

public class DefaultEntitlementModule extends AbstractModule implements EntitlementModule {


    public DefaultEntitlementModule(final ConfigSource configSource) {
    }

    @Override
    protected void configure() {
        installAccountUserApi();
        installSubscriptionUserApi();
        installBlockingStateDao();
        installBlockingApi();
        installEntitlementApi();
        installBlockingChecker();
    }

    @Override
    public void installBlockingStateDao() {
        bind(BlockingStateDao.class).to(DefaultBlockingStateDao.class).asEagerSingleton();
    }

    @Override
    public void installBlockingApi() {
        bind(BlockingInternalApi.class).to(DefaultInternalBlockingApi.class).asEagerSingleton();
    }

    @Override
    public void installAccountUserApi() {
        bind(AccountUserApi.class).to(BlockingAccountUserApi.class).asEagerSingleton();
    }


    @Override
    public void installSubscriptionUserApi() {
        bind(SubscriptionUserApi.class).to(BlockingSubscriptionUserApi.class).asEagerSingleton();
    }

    @Override
    public void installEntitlementApi() {
        bind(EntitlementApi.class).to(DefaultEntitlementApi.class).asEagerSingleton();
    }

    public void installBlockingChecker() {
        bind(BlockingChecker.class).to(DefaultBlockingChecker.class).asEagerSingleton();
    }


}
