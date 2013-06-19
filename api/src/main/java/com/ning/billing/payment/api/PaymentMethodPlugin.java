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

package com.ning.billing.payment.api;

import java.util.List;

public interface PaymentMethodPlugin {


    /**
     *
     * @return the id from the plugin
     */
    public String getExternalPaymentMethodId();

    /**
     *
     * @return whether plugin sees that PM as being the default
     */
    public boolean isDefaultPaymentMethod();

    /**
     *
     * @return the list of key/value pair set by the plugin
     */
    public List<PaymentMethodKVInfo> getProperties();

    /**
     *
     * @return the payment method type name if applicable
     */
    public String getType();

    /**
     *
     * @return the credit card name if applicable
     */
    public String getCCName();

    /**
     *
     * @return the credit card type if applicable
     */
    public String getCCType();

    /**
     *
     * @return the credit card expiration month
     */
    public String getCCExpirationMonth();

    /**
     *
     * @return the credit card expiration year
     */
    public String getCCExpirationYear();

    /**
     *
     * @return the credit card last 4 numbers
     */
    public String getCCLast4();

    /**
     *
     * @return the address line 1
     */
    public String getAddress1();

    /**
     *
     * @return the address line 2
     */
    public String getAddress2();

    /**
     *
     * @return the city
     */
    public String getCity();

    /**
     *
     * @return the state
     */
    public String getState();

    /**
     *
     * @return the zip
     */
    public String getZip();

    /**
     *
     * @return the country
     */
    public String getCountry();


}
