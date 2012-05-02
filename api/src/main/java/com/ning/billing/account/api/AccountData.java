/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.account.api;

import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.junction.api.BlockingState;

public interface AccountData {

    public String getExternalKey();

    public String getName();

    public int getFirstNameLength();

    public String getEmail();

    public int getBillCycleDay();

    public Currency getCurrency();

    public String getPaymentProviderName();

    public DateTimeZone getTimeZone();

    public String getLocale();

    public String getAddress1();

    public String getAddress2();

    public String getCompanyName();

    public String getCity();

    public String getStateOrProvince();

    public String getPostalCode();

    public String getCountry();

    public String getPhone();

}
