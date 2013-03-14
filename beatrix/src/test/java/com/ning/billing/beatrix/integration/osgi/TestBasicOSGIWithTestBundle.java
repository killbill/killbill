/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.beatrix.integration.osgi;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.beatrix.osgi.SetupBundleWithAssertion;
import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.osgi.glue.OSGIDataSourceConfig;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;

import static com.jayway.awaitility.Awaitility.await;

/**
 * Basic OSGI test that relies on the 'test' bundle (com.ning.billing.osgi.bundles.test.TestActivator)
 * <p/>
 * The test checks that the bundle:
 * - gets started
 * - can make API call
 * - can listen to KB events
 * - can register a service (PaymentPluginApi) that this test calls
 * - can write in the DB using the DataSource (this is how the assertion work)
 */
public class TestBasicOSGIWithTestBundle extends TestOSGIBase {

    private final String BUNDLE_TEST_RESOURCE = "killbill-osgi-bundles-test-beatrix";

    @Inject
    private OSGIServiceRegistration<PaymentPluginApi> paymentPluginApiOSGIServiceRegistration;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {

        final String jdbcConnection = getDBTestingHelper().getJdbcConnectionString();
        final String userName = DBTestingHelper.USERNAME;
        final String userPwd = DBTestingHelper.PASSWORD;

        System.setProperty(OSGIDataSourceConfig.DATA_SOURCE_PROP_PREFIX + "jdbc.url", jdbcConnection);
        System.setProperty(OSGIDataSourceConfig.DATA_SOURCE_PROP_PREFIX + "jdbc.user", userName);
        System.setProperty(OSGIDataSourceConfig.DATA_SOURCE_PROP_PREFIX + "jdbc.password", userPwd);

        // OSGIDataSourceConfig
        super.beforeClass();

        // This is extracted from surefire system configuration-- needs to be added explicitly in IntelliJ for correct running
        final String killbillVersion = System.getProperty("killbill.version");
        SetupBundleWithAssertion setupTest = new SetupBundleWithAssertion(BUNDLE_TEST_RESOURCE, osgiConfig, killbillVersion);
        setupTest.setupBundle();

    }

    @Test(groups = "slow")
    public void testBundleTest() throws Exception {

        // At this point test bundle should have been started already
        final TestActivatorWithAssertion assertTor = new TestActivatorWithAssertion(getDBI());
        assertTor.assertPluginInitialized();

        // Create an account and expect test bundle listen to KB events and write the external name in its table
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertTor.assertPluginReceievdAccountCreationEvent(account.getExternalKey());

        // Retrieve the PaymentPluginApi that the test bundle registered
        final PaymentPluginApi paymentPluginApi = getTestPluginPaymentApi();

        // Make a payment and expect test bundle to correcly write in its table the input values
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal paymentAmount = new BigDecimal("14.32");
        final PaymentInfoPlugin r = paymentPluginApi.processPayment(paymentId, account.getPaymentMethodId(), paymentAmount, callContext);
        assertTor.assertPluginCreatedPayment(paymentId, account.getPaymentMethodId(), paymentAmount);
    }

    private PaymentPluginApi getTestPluginPaymentApi() {
        PaymentPluginApi result = paymentPluginApiOSGIServiceRegistration.getServiceForName("test");
        Assert.assertNotNull(result);
        return result;
    }

    private static final class TestActivatorWithAssertion {

        private final IDBI dbi;

        public TestActivatorWithAssertion(final IDBI dbi) {
            this.dbi = dbi;
        }

        public void assertPluginInitialized() {
            assertWithCallback(new AwaitCallback() {
                @Override
                public boolean isSuccess() {
                    return isPluginInitialized();
                }
            }, "Plugin did not complete initialization");
        }

        public void assertPluginReceievdAccountCreationEvent(final String expectedExternalKey) {
            assertWithCallback(new AwaitCallback() {
                @Override
                public boolean isSuccess() {
                    return isValidAccountExternalKey(expectedExternalKey);
                }
            }, "Plugin did not receive account creation event");
        }

        public void assertPluginCreatedPayment(final UUID expectedPaymentId, final UUID expectedPaymentMethodId, final BigDecimal expectedAmount) {
            assertWithCallback(new AwaitCallback() {
                @Override
                public boolean isSuccess() {
                    return isValidPayment(expectedPaymentId, expectedPaymentMethodId, expectedAmount);
                }
            }, "Plugin did not create the payment");
        }


        private void assertWithCallback(final AwaitCallback callback, final String error) {
            try {
                await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return callback.isSuccess();
                    }
                });
            } catch (Exception e) {
                Assert.fail(error, e);
            }
        }

        private boolean isValidPayment(final UUID expectedPaymentId, final UUID expectedPaymentMethodId, final BigDecimal expectedAmount) {
            TestModel test = getTestModelFirstRecord();
            return expectedPaymentId.equals(test.getPaymentId()) &&
                   expectedPaymentMethodId.equals(test.getPaymentMethodId()) &&
                   expectedAmount.compareTo(test.getAmount()) == 0;
        }


        private boolean isPluginInitialized() {
            TestModel test = getTestModelFirstRecord();
            return test.isStarted();
        }

        private boolean isValidAccountExternalKey(final String expectedExternalKey) {
            TestModel test = getTestModelFirstRecord();
            return expectedExternalKey.equals(test.getAccountExternalKey());
        }

        private TestModel getTestModelFirstRecord() {
            TestModel test = dbi.inTransaction(new TransactionCallback<TestModel>() {
                @Override
                public TestModel inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                    Query<Map<String, Object>> q = conn.createQuery("SELECT is_started, external_key, payment_id, payment_method_id, payment_amount FROM test_bundle WHERE record_id = 1;");
                    TestModel test = q.map(new TestMapper()).first();
                    return test;
                }
            });
            return test;
        }
    }


    private final static class TestModel {

        private final Boolean isStarted;
        private final String accountExternalKey;
        private final UUID paymentId;
        private final UUID paymentMethodId;
        private final BigDecimal amount;

        private TestModel(final Boolean started, final String accountExternalKey, final UUID paymentId, final UUID paymentMethodId, final BigDecimal amount) {
            isStarted = started;
            this.accountExternalKey = accountExternalKey;
            this.paymentId = paymentId;
            this.paymentMethodId = paymentMethodId;
            this.amount = amount;
        }

        public Boolean isStarted() {
            return isStarted;
        }

        public String getAccountExternalKey() {
            return accountExternalKey;
        }

        public UUID getPaymentId() {
            return paymentId;
        }

        public UUID getPaymentMethodId() {
            return paymentMethodId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

    }


    private static class TestMapper implements ResultSetMapper<TestModel> {

        @Override
        public TestModel map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {

            final Boolean isStarted = r.getBoolean("is_started");
            final String externalKey = r.getString("external_key");
            final UUID paymentId = r.getString("payment_id") != null ? UUID.fromString(r.getString("payment_id")) : null;
            final UUID paymentMethodId = r.getString("payment_method_id") != null ? UUID.fromString(r.getString("payment_method_id")) : null;
            final BigDecimal amount = r.getBigDecimal("payment_amount");
            return new TestModel(isStarted, externalKey, paymentId, paymentMethodId, amount);
        }
    }

    private interface AwaitCallback {
        boolean isSuccess();
    }
}
