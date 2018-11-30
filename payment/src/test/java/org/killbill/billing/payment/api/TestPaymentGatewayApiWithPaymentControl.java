/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultOnSuccessPaymentControlResult;
import org.killbill.billing.payment.retry.DefaultPriorPaymentControlResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class TestPaymentGatewayApiWithPaymentControl extends PaymentTestSuiteNoDB {

    @Inject
    private OSGIServiceRegistration<PaymentControlPluginApi> controlPluginRegistry;

    private Account account;

    private PaymentOptions paymentOptions;

    private TestPaymentGatewayApiControlPlugin plugin;
    private TestPaymentGatewayApiValidationPlugin validationPlugin;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();

        account = testHelper.createTestAccount("arthur@gmail.com", true);

        paymentOptions = new PaymentOptions() {
            @Override
            public boolean isExternalPayment() {
                return false;
            }

            @Override
            public List<String> getPaymentControlPluginNames() {
                return ImmutableList.of(TestPaymentGatewayApiControlPlugin.PLUGIN_NAME, TestPaymentGatewayApiValidationPlugin.VALIDATION_PLUGIN_NAME);
            }
        };

        plugin = new TestPaymentGatewayApiControlPlugin();

        controlPluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getPluginName() {
                return TestPaymentGatewayApiControlPlugin.PLUGIN_NAME;
            }

            @Override
            public String getRegistrationName() {
                return TestPaymentGatewayApiControlPlugin.PLUGIN_NAME;
            }
        }, plugin);

        validationPlugin = new TestPaymentGatewayApiValidationPlugin();
        controlPluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getPluginName() {
                return TestPaymentGatewayApiValidationPlugin.VALIDATION_PLUGIN_NAME;
            }

            @Override
            public String getRegistrationName() {
                return TestPaymentGatewayApiValidationPlugin.VALIDATION_PLUGIN_NAME;
            }
        }, validationPlugin);

    }

    @Test(groups = "fast")
    public void testBuildFormDescriptorWithPaymentControl() throws PaymentApiException {

        final List<PluginProperty> initialProperties = new ArrayList<PluginProperty>();
        initialProperties.add(new PluginProperty("keyA", "valueA", true));
        initialProperties.add(new PluginProperty("keyB", "valueB", true));
        initialProperties.add(new PluginProperty("keyC", "valueC", true));

        final List<PluginProperty> priorNewProperties = new ArrayList<PluginProperty>();
        priorNewProperties.add(new PluginProperty("keyD", "valueD", true));
        final List<PluginProperty> priorRemovedProperties = new ArrayList<PluginProperty>();
        priorRemovedProperties.add(new PluginProperty("keyA", "valueA", true));
        plugin.setPriorCallProperties(priorNewProperties, priorRemovedProperties);

        final List<PluginProperty> onResultNewProperties = new ArrayList<PluginProperty>();
        onResultNewProperties.add(new PluginProperty("keyE", "valueE", true));
        final List<PluginProperty> onResultRemovedProperties = new ArrayList<PluginProperty>();
        onResultRemovedProperties.add(new PluginProperty("keyB", "valueB", true));
        plugin.setOnResultProperties(onResultNewProperties, onResultRemovedProperties);

        final List<PluginProperty> expectedPriorCallProperties = new ArrayList<PluginProperty>();
        expectedPriorCallProperties.add(new PluginProperty("keyB", "valueB", true));
        expectedPriorCallProperties.add(new PluginProperty("keyC", "valueC", true));
        expectedPriorCallProperties.add(new PluginProperty("keyD", "valueD", true));

        validationPlugin.setExpectedPriorCallProperties(expectedPriorCallProperties);

        final List<PluginProperty> expectedProperties = new ArrayList<PluginProperty>();
        expectedProperties.add(new PluginProperty("keyC", "valueC", true));
        expectedProperties.add(new PluginProperty("keyD", "valueD", true));
        expectedProperties.add(new PluginProperty("keyE", "valueE", true));

        validationPlugin.setExpectedProperties(expectedProperties);

        // Set a random UUID to verify the plugin will successfully override it
        paymentGatewayApi.buildFormDescriptorWithPaymentControl(account, UUID.randomUUID(), ImmutableList.<PluginProperty>of(), initialProperties, paymentOptions, callContext);

    }

    @Test(groups = "fast")
    public void testBuildFormDescriptorWithPaymentControlAbortedPayment() throws PaymentApiException {
        plugin.setAborted(true);

        // Set a random UUID to verify the plugin will successfully override it
        try {
            paymentGatewayApi.buildFormDescriptorWithPaymentControl(account, UUID.randomUUID(), ImmutableList.<PluginProperty>of(), ImmutableList.<PluginProperty>of(), paymentOptions, callContext);
            Assert.fail();
        } catch (PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode());
        }

    }

    public static class TestPaymentGatewayApiValidationPlugin implements PaymentControlPluginApi {

        public static final String VALIDATION_PLUGIN_NAME = "TestPaymentGatewayApiValidationPlugin";

        private Iterable<PluginProperty> expectedPriorCallProperties;
        private Iterable<PluginProperty> expectedProperties;

        public TestPaymentGatewayApiValidationPlugin() {
            this.expectedPriorCallProperties = ImmutableList.of();
            this.expectedProperties = ImmutableList.of();
        }

        public void setExpectedProperties(final Iterable<PluginProperty> expectedProperties) {
            this.expectedProperties = expectedProperties;
        }

        public void setExpectedPriorCallProperties(final List<PluginProperty> expectedPriorCallProperties) {
            this.expectedPriorCallProperties = expectedPriorCallProperties;
        }

        @Override
        public PriorPaymentControlResult priorCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            validate(properties, expectedPriorCallProperties);
            return new DefaultPriorPaymentControlResult(false);
        }

        @Override
        public OnSuccessPaymentControlResult onSuccessCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            validate(properties, expectedProperties);
            return new DefaultOnSuccessPaymentControlResult();
        }

        @Override
        public OnFailurePaymentControlResult onFailureCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            validate(properties, expectedProperties);
            return new DefaultFailureCallResult();
        }

        private static void validate(final Iterable<PluginProperty> properties, final Iterable<PluginProperty> expected) {
            Assert.assertEquals(Iterables.size(properties), Iterables.size(expected), "Got " + Iterables.size(properties) + "properties" + ", expected " + Iterables.size(expected));

            for (final PluginProperty curExpected : expected) {
                Assert.assertTrue(Iterables.any(properties, new Predicate<PluginProperty>() {
                    @Override
                    public boolean apply(final PluginProperty input) {
                        return input.getKey().equals(curExpected.getKey()) && input.getValue().equals(curExpected.getValue());

                    }
                }), "Cannot find expected property" + curExpected.getKey());
            }
        }

    }

    public class TestPaymentGatewayApiControlPlugin implements PaymentControlPluginApi {

        public static final String PLUGIN_NAME = "TestPaymentGatewayApiControlPlugin";

        private Iterable<PluginProperty> newPriorCallProperties;
        private Iterable<PluginProperty> removedPriorCallProperties;

        private Iterable<PluginProperty> newOnResultProperties;
        private Iterable<PluginProperty> removedOnResultProperties;

        private boolean aborted;

        public TestPaymentGatewayApiControlPlugin() {
            this.aborted = false;
            this.newPriorCallProperties = ImmutableList.of();
            this.removedPriorCallProperties = ImmutableList.of();
            this.newOnResultProperties = ImmutableList.of();
            this.removedOnResultProperties = ImmutableList.of();
        }

        public void setAborted(final boolean aborted) {
            this.aborted = aborted;
        }

        public void setPriorCallProperties(final Iterable<PluginProperty> newPriorCallProperties, final Iterable<PluginProperty> removedPriorCallProperties) {
            this.newPriorCallProperties = newPriorCallProperties;
            this.removedPriorCallProperties = removedPriorCallProperties;
        }

        public void setOnResultProperties(final Iterable<PluginProperty> newOnResultProperties, final Iterable<PluginProperty> removedOnResultProperties) {
            this.newOnResultProperties = newOnResultProperties;
            this.removedOnResultProperties = removedOnResultProperties;
        }

        @Override
        public PriorPaymentControlResult priorCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            return new DefaultPriorPaymentControlResult(aborted, account.getPaymentMethodId(), null, null, null, getAdjustedProperties(properties, newPriorCallProperties, removedPriorCallProperties));
        }

        @Override
        public OnSuccessPaymentControlResult onSuccessCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            return new DefaultOnSuccessPaymentControlResult(getAdjustedProperties(properties, newOnResultProperties, removedOnResultProperties));
        }

        @Override
        public OnFailurePaymentControlResult onFailureCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            return new DefaultFailureCallResult(null, getAdjustedProperties(properties, newOnResultProperties, removedOnResultProperties));
        }

        private Iterable<PluginProperty> getAdjustedProperties(final Iterable<PluginProperty> input, final Iterable<PluginProperty> newProperties, final Iterable<PluginProperty> removedProperties) {
            final Iterable<PluginProperty> filtered = Iterables.filter(input, new Predicate<PluginProperty>() {
                @Override
                public boolean apply(final PluginProperty p) {
                    final boolean toBeRemoved = Iterables.any(removedProperties, new Predicate<PluginProperty>() {
                        @Override
                        public boolean apply(final PluginProperty a) {
                            return a.getKey().equals(p.getKey()) && a.getValue().equals(p.getValue());
                        }
                    });
                    return !toBeRemoved;
                }
            });
            return Iterables.concat(filtered, newProperties);
        }
    }

}
