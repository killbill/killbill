package com.ning.billing.entitlement;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.ning.billing.GuicyKillbillTestSuiteNoDB;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.glue.TestEntitlementModuleNoDB;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.dao.TagDao;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

public abstract class EntitlementTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected BlockingInternalApi blockingInternalApi;
    @Inject
    protected BlockingStateDao blockingStateDao;
    @Inject
    protected CatalogService catalogService;
    @Inject
    @RealImplementation
    protected SubscriptionUserApi subscriptionUserApi;
    @Inject
    protected SubscriptionInternalApi subscriptionInternalApi;
    @Inject
    protected PersistentBus bus;
    @Inject
    protected TagDao tagDao;
    @Inject
    protected TagInternalApi tagInternalApi;

    @Inject
    protected BlockingChecker blockingChecker;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(new TestEntitlementModuleNoDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        bus.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() {
        bus.stop();
    }


}
