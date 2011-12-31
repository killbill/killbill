package com.ning.billing.payment.api;

import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.payment.setup.PaymentTestModule;

@Guice(modules = PaymentTestModule.class)
@Test(groups = "fast")
public class TestMockPaymentApi extends TestPaymentApi {

}
