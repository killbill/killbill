package com.ning.billing.beatrix.integration.osgi;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.beatrix.osgi.SetupBundleWithAssertion;

public class TestJrubyNotificationPlugin extends TestOSGIBase {

    private final String BUNDLE_TEST_RESOURCE_PREFIX = "killbill-notification-test";
    private final String BUNDLE_TEST_RESOURCE = BUNDLE_TEST_RESOURCE_PREFIX + ".tar.gz";

    @BeforeClass(groups = "slow", enabled = true)
    public void beforeClass() throws Exception {

        // OSGIDataSourceConfig
        super.beforeClass();

        // This is extracted from surefire system configuration-- needs to be added explicitly in IntelliJ for correct running
        final String killbillVersion = System.getProperty("killbill.version");

        SetupBundleWithAssertion setupTest = new SetupBundleWithAssertion(BUNDLE_TEST_RESOURCE, osgiConfig, killbillVersion);
        setupTest.setupJrubyBundle();
    }

    @Test(groups = "slow", enabled = true)
    public void testOnEventForAccountCreation() throws Exception {

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(4));
    }

}
