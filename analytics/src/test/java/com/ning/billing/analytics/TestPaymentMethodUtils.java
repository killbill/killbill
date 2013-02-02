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

package com.ning.billing.analytics;

import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.payment.api.PaymentMethodPlugin;

public class TestPaymentMethodUtils {

    @Test(groups = "fast")
    public void testUnknowns() throws Exception {
        Assert.assertNull(PaymentMethodUtils.getCardCountry(null));
        Assert.assertNull(PaymentMethodUtils.getCardType(null));
        Assert.assertNull(PaymentMethodUtils.getPaymentMethodType(null));

        final PaymentMethodPlugin paymentMethodPlugin = Mockito.mock(PaymentMethodPlugin.class);
        Assert.assertNull(PaymentMethodUtils.getCardCountry(paymentMethodPlugin));
        Assert.assertNull(PaymentMethodUtils.getCardType(paymentMethodPlugin));
        Assert.assertNull(PaymentMethodUtils.getPaymentMethodType(paymentMethodPlugin));
    }

    @Test(groups = "fast")
    public void testCardCountry() throws Exception {
        final String country = UUID.randomUUID().toString();
        final String cardType = UUID.randomUUID().toString();
        final String type = UUID.randomUUID().toString();

        final PaymentMethodPlugin paymentMethodPlugin = Mockito.mock(PaymentMethodPlugin.class);
        Mockito.when(paymentMethodPlugin.getValueString(PaymentMethodUtils.COUNTRY_KEY)).thenReturn(country);
        Mockito.when(paymentMethodPlugin.getValueString(PaymentMethodUtils.CARD_TYPE_KEY)).thenReturn(cardType);
        Mockito.when(paymentMethodPlugin.getValueString(PaymentMethodUtils.TYPE_KEY)).thenReturn(type);

        Assert.assertEquals(PaymentMethodUtils.getCardCountry(paymentMethodPlugin), country);
        Assert.assertEquals(PaymentMethodUtils.getCardType(paymentMethodPlugin), cardType);
        Assert.assertEquals(PaymentMethodUtils.getPaymentMethodType(paymentMethodPlugin), type);
    }
}
