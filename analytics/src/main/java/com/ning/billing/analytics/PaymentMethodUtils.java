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

import javax.annotation.Nullable;

import com.ning.billing.payment.api.PaymentMethodPlugin;

import com.google.common.annotations.VisibleForTesting;

// TODO - make it generic
public class PaymentMethodUtils {

    @VisibleForTesting
    static final String COUNTRY_KEY = "country";
    @VisibleForTesting
    static final String CARD_TYPE_KEY = "cardType";
    @VisibleForTesting
    static final String TYPE_KEY = "type";

    private PaymentMethodUtils() {}

    public static String getCardCountry(@Nullable final PaymentMethodPlugin pluginDetail) {
        if (pluginDetail == null) {
            return null;
        }

        return pluginDetail.getValueString(COUNTRY_KEY);
    }

    public static String getCardType(@Nullable final PaymentMethodPlugin pluginDetail) {
        if (pluginDetail == null) {
            return null;
        }

        return pluginDetail.getValueString(CARD_TYPE_KEY);
    }

    public static String getPaymentMethodType(@Nullable final PaymentMethodPlugin pluginDetail) {
        if (pluginDetail == null) {
            return null;
        }

        return pluginDetail.getValueString(TYPE_KEY);
    }
}
