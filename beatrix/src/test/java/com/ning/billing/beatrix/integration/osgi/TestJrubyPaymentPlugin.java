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
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.beatrix.osgi.SetupBundleWithAssertion;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin.PaymentPluginStatus;
import com.ning.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.RefundInfoPlugin;
import com.ning.billing.payment.plugin.api.RefundInfoPlugin.RefundPluginStatus;

public class TestJrubyPaymentPlugin extends TestOSGIBase {

    private final String BUNDLE_TEST_RESOURCE_PREFIX = "killbill-payment-test";
    private final String BUNDLE_TEST_RESOURCE = BUNDLE_TEST_RESOURCE_PREFIX + ".tar.gz";

    @Inject
    private OSGIServiceRegistration<PaymentPluginApi> paymentPluginApiOSGIServiceRegistration;

    @BeforeClass(groups = "slow", enabled = true)
    public void beforeClass() throws Exception {


        // OSGIDataSourceConfig
        super.beforeClass();

        // This is extracted from surefire system configuration-- needs to be added explicitly in IntelliJ for correct running
        final String killbillVersion = System.getProperty("killbill.version");

        SetupBundleWithAssertion setupTest = new SetupBundleWithAssertion(BUNDLE_TEST_RESOURCE, osgiConfig, killbillVersion);
        //setupTest.setupJrubyBundle();

    }

    @Test(groups = "slow", enabled = true)
    public void testProcessPayment() throws Exception {

        PaymentPluginApi api = getTestPluginPaymentApi();

        final DateTime beforeCall = new DateTime().toDateTime(DateTimeZone.UTC).minusSeconds(1);
        PaymentInfoPlugin res = api.processPayment(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, callContext);
        final DateTime afterCall = new DateTime().toDateTime(DateTimeZone.UTC).plusSeconds(1);


        Assert.assertTrue(res.getAmount().compareTo(BigDecimal.TEN) == 0);
        Assert.assertTrue(res.getCreatedDate().compareTo(beforeCall) >= 0);
        Assert.assertTrue(res.getCreatedDate().compareTo(afterCall) <= 0);

        Assert.assertTrue(res.getEffectiveDate().compareTo(beforeCall) >= 0);
        Assert.assertTrue(res.getEffectiveDate().compareTo(afterCall) <= 0);

        Assert.assertEquals(res.getGatewayError(), "gateway_error");
        Assert.assertEquals(res.getGatewayErrorCode(), "gateway_error_code");

        Assert.assertEquals(res.getStatus(), PaymentPluginStatus.PROCESSED);
    }

    @Test(groups = "slow", enabled = true)
    public void testGetPaymentInfo() throws Exception {

        PaymentPluginApi api = getTestPluginPaymentApi();

        final DateTime beforeCall = new DateTime().toDateTime(DateTimeZone.UTC).minusSeconds(1);
        PaymentInfoPlugin res = api.getPaymentInfo(UUID.randomUUID(), callContext);
        final DateTime afterCall = new DateTime().toDateTime(DateTimeZone.UTC).plusSeconds(1);


        Assert.assertTrue(res.getAmount().compareTo(BigDecimal.ZERO) == 0);
        Assert.assertTrue(res.getCreatedDate().compareTo(beforeCall) >= 0);
        Assert.assertTrue(res.getCreatedDate().compareTo(afterCall) <= 0);

        Assert.assertTrue(res.getEffectiveDate().compareTo(beforeCall) >= 0);
        Assert.assertTrue(res.getEffectiveDate().compareTo(afterCall) <= 0);

        Assert.assertEquals(res.getGatewayError(), "gateway_error");
        Assert.assertEquals(res.getGatewayErrorCode(), "gateway_error_code");

        Assert.assertEquals(res.getStatus(), PaymentPluginStatus.PROCESSED);
    }


    @Test(groups = "slow", enabled = true)
    public void testProcessRefund() throws Exception {

        PaymentPluginApi api = getTestPluginPaymentApi();

        final DateTime beforeCall = new DateTime().toDateTime(DateTimeZone.UTC).minusSeconds(1);
        RefundInfoPlugin res = api.processRefund(UUID.randomUUID(), BigDecimal.TEN, callContext);
        final DateTime afterCall = new DateTime().toDateTime(DateTimeZone.UTC).plusSeconds(1);


        Assert.assertTrue(res.getAmount().compareTo(BigDecimal.TEN) == 0);
        Assert.assertTrue(res.getCreatedDate().compareTo(beforeCall) >= 0);
        Assert.assertTrue(res.getCreatedDate().compareTo(afterCall) <= 0);

        Assert.assertTrue(res.getEffectiveDate().compareTo(beforeCall) >= 0);
        Assert.assertTrue(res.getEffectiveDate().compareTo(afterCall) <= 0);

        Assert.assertEquals(res.getGatewayError(), "gateway_error");
        Assert.assertEquals(res.getGatewayErrorCode(), "gateway_error_code");

        Assert.assertEquals(res.getStatus(), RefundPluginStatus.PROCESSED);
    }

    @Test(groups = "slow", enabled = true)
    public void testAddPaymentMethod() throws Exception {

        PaymentPluginApi api = getTestPluginPaymentApi();

        final DateTime beforeCall = new DateTime().toDateTime(DateTimeZone.UTC).minusSeconds(1);
        final PaymentMethodPlugin info = createPaymentMethodPlugin();
        api.addPaymentMethod(UUID.randomUUID(), UUID.randomUUID(), info, true, callContext);
        final DateTime afterCall = new DateTime().toDateTime(DateTimeZone.UTC).plusSeconds(1);
    }


    @Test(groups = "slow", enabled = true)
    public void testDeletePaymentMethod() throws Exception {

        PaymentPluginApi api = getTestPluginPaymentApi();
        api.deletePaymentMethod(UUID.randomUUID(), callContext);
    }

    @Test(groups = "slow", enabled = true)
    public void testGetPaymentMethodDetail() throws Exception {

        PaymentPluginApi api = getTestPluginPaymentApi();
        final PaymentMethodPlugin res = api.getPaymentMethodDetail(UUID.randomUUID(), UUID.randomUUID(), callContext);

        Assert.assertEquals(res.getExternalPaymentMethodId(), "foo");
        Assert.assertTrue(res.isDefaultPaymentMethod());
        Assert.assertEquals(res.getProperties().size(), 0);
    }

    @Test(groups = "slow", enabled = true)
    public void testSetDefaultPaymentMethod() throws Exception {

        PaymentPluginApi api = getTestPluginPaymentApi();
        final PaymentMethodPlugin info = createPaymentMethodPlugin();
        api.setDefaultPaymentMethod(UUID.randomUUID(), callContext);
    }

    @Test(groups = "slow", enabled = true)
    public void testGetPaymentMethods() throws Exception {

        PaymentPluginApi api = getTestPluginPaymentApi();
        final List<PaymentMethodInfoPlugin> res = api.getPaymentMethods(UUID.randomUUID(), true, callContext);

        Assert.assertEquals(res.size(), 1);

        final PaymentMethodInfoPlugin res0 = res.get(0);
        Assert.assertTrue(res0.isDefault());
        Assert.assertEquals(res0.getExternalPaymentMethodId(), "external_payment_method_id");
    }


    private PaymentPluginApi getTestPluginPaymentApi() {
        PaymentPluginApi result = paymentPluginApiOSGIServiceRegistration.getServiceForName(BUNDLE_TEST_RESOURCE_PREFIX);
        Assert.assertNotNull(result);
        return result;
    }
}
