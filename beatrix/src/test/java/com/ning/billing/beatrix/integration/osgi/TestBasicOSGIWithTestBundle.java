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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
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
import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.osgi.api.config.PluginConfig.PluginType;
import com.ning.billing.osgi.api.config.PluginJavaConfig;
import com.ning.billing.osgi.glue.OSGIDataSourceConfig;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.util.config.OSGIConfig;

import com.google.common.io.Resources;

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

    private final String BUNDLE_TEST_RESOURCE = "killbill-osgi-bundles-test";

    @Inject
    private OSGIServiceRegistration<PaymentPluginApi> paymentPluginApiOSGIServiceRegistration;

    @BeforeClass(groups = "slow")
    public void setup() throws Exception {

        final String jdbcConnection = getDBTestingHelper().getJdbcConnectionString();
        final String userName = DBTestingHelper.USERNAME;
        final String userPwd = DBTestingHelper.PASSWORD;

        System.setProperty(OSGIDataSourceConfig.DATA_SOURCE_PROP_PREFIX + "jdbc.url", jdbcConnection);
        System.setProperty(OSGIDataSourceConfig.DATA_SOURCE_PROP_PREFIX + "jdbc.user", userName);
        System.setProperty(OSGIDataSourceConfig.DATA_SOURCE_PROP_PREFIX + "jdbc.password", userPwd);

        // OSGIDataSourceConfig
        super.setup();

        // This is extracted from surefire system configuration-- needs to be added explicitely in IntelliJ for correct running
        final String killbillVersion = System.getProperty("killbill.version");
        SetupBundleWithAssertion setupTest = new SetupBundleWithAssertion(BUNDLE_TEST_RESOURCE, config, killbillVersion);
        setupTest.setupBundle();

    }

    @Test(groups = "slow")
    public void testBundleTest() throws Exception {

        // At this point test bundle should have been started already
        final TestActivatorWithAssertion assertTor = new TestActivatorWithAssertion(getDBI());
        assertTor.assertPluginInitialized();

        // Create an account and expect test bundle listen to KB events and write the external name in its table
        final Account account = createAccountWithPaymentMethod(getAccountData(1));
        assertTor.assertPluginReceievdAccountCreationEvent(account.getExternalKey());

        // Retrieve the PaymentPluginApi that the test bundle registered
        final PaymentPluginApi paymentPluginApi = getTestPluginPaymentApi();

        // Make a payment and expect test bundle to correcly write in its table the input values
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal paymentAmount = new BigDecimal("14.32");
        final PaymentInfoPlugin r = paymentPluginApi.processPayment(paymentId, account.getPaymentMethodId(), paymentAmount, callContext);
        assertTor.assertPluginCreatedPayment(paymentId, account.getPaymentMethodId(), paymentAmount);
    }

    private static final class SetupBundleWithAssertion {

        private final String bundleName;
        private final OSGIConfig config;
        private final String killbillVersion;

        public SetupBundleWithAssertion(final String bundleName, final OSGIConfig config, final String killbillVersion) {
            this.bundleName = bundleName;
            this.config = config;
            this.killbillVersion = killbillVersion;
        }

        public void setupBundle() {

            try {
                // Retrieve PluginConfig info from classpath
                // test bundle should have been exported under Beatrix resource by the maven maven-dependency-plugin
                final PluginJavaConfig pluginConfig = extractBundleTestResource();
                Assert.assertNotNull(pluginConfig);

                // Create OSGI install bundle directory
                setupDirectoryStructure(pluginConfig);

                // Copy the jar
                copyFile(new File(pluginConfig.getBundleJarPath()), new File(pluginConfig.getPluginVersionRoot().getAbsolutePath(), pluginConfig.getPluginVersionnedName() + ".jar"));

                // Create the config file
                createConfigFile(pluginConfig);

            } catch (IOException e) {
                Assert.fail(e.getMessage());
            }
        }

        private void createConfigFile(final PluginJavaConfig pluginConfig) throws IOException {

            PrintStream printStream = null;
            try {
                final File configFile = new File(pluginConfig.getPluginVersionRoot(), config.getOSGIKillbillPropertyName());
                configFile.createNewFile();
                printStream = new PrintStream(new FileOutputStream(configFile));
                printStream.print("pluginType=" + PluginType.NOTIFICATION);
            } finally {
                if (printStream != null) {
                    printStream.close();
                }
            }
        }

        private void setupDirectoryStructure(final PluginJavaConfig pluginConfig) {

            final File rootDir  = new File(config.getRootInstallationDir());
            if (rootDir.exists()) {
                deleteDirectory(rootDir, false);
            }
            pluginConfig.getPluginVersionRoot().mkdirs();
        }

        private static void deleteDirectory(final File path, final boolean deleteParent) {
            if (path == null) {
                return;
            }

            if (path.exists()) {
                final File[] files = path.listFiles();
                if (files != null) {
                    for (final File f : files) {
                        if (f.isDirectory()) {
                            deleteDirectory(f, true);
                        }
                        f.delete();
                    }
                }

                if (deleteParent) {
                    path.delete();
                }
            }
        }



        private PluginJavaConfig extractBundleTestResource() {

            final String resourceName = bundleName + "-" + killbillVersion + "-jar-with-dependencies.jar";
            final URL resourceUrl = Resources.getResource(resourceName);
            if (resourceUrl != null) {
                final String[] parts = resourceUrl.getPath().split("/");
                final String lastPart = parts[parts.length - 1];
                if (lastPart.startsWith(bundleName)) {
                    return createPluginConfig(resourceUrl.getPath(), lastPart);
                }
            }
            return null;

        }

        private PluginJavaConfig createPluginConfig(final String bundleTestResourcePath, final String fileName) {

            return new PluginJavaConfig() {
                @Override
                public String getBundleJarPath() {
                    return bundleTestResourcePath;
                }

                @Override
                public String getPluginName() {
                    return bundleName;
                }

                @Override
                public PluginType getPluginType() {
                    return PluginType.PAYMENT;
                }

                @Override
                public String getVersion() {
                    return killbillVersion;
                }

                @Override
                public String getPluginVersionnedName() {
                    return bundleName + "-" + killbillVersion;
                }

                @Override
                public File getPluginVersionRoot() {
                    final StringBuilder tmp = new StringBuilder(config.getRootInstallationDir());
                    tmp.append("/")
                       .append(PluginLanguage.JAVA.toString().toLowerCase())
                       .append("/")
                       .append(bundleName)
                       .append("/")
                       .append(killbillVersion);
                    final File result = new File(tmp.toString());
                    return result;
                }

                @Override
                public PluginLanguage getPluginLanguage() {
                    return PluginLanguage.JAVA;
                }
            };
        }

        public static void copyFile(File sourceFile, File destFile) throws IOException {
            if (!destFile.exists()) {
                destFile.createNewFile();
            }

            FileChannel source = null;
            FileChannel destination = null;

            try {
                source = new FileInputStream(sourceFile).getChannel();
                destination = new FileOutputStream(destFile).getChannel();
                destination.transferFrom(source, 0, source.size());
            } finally {
                if (source != null) {
                    source.close();
                }
                if (destination != null) {
                    destination.close();
                }
            }
        }

    }


    private PaymentPluginApi getTestPluginPaymentApi() {
        PaymentPluginApi result = paymentPluginApiOSGIServiceRegistration.getServiceForPluginName("test");
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
