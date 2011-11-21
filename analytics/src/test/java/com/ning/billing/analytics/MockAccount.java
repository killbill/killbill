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

package com.ning.billing.analytics;

import com.ning.billing.account.api.IAccount;
import com.ning.billing.catalog.api.Currency;

import java.util.UUID;

public class MockAccount implements IAccount
{
    private final String key;

    public MockAccount(final String key)
    {
        this.key = key;
    }

    @Override
    public String getName()
    {
        return "accountName";
    }

    @Override
    public String getEmail()
    {
        return "accountName@yahoo.com";
    }

    @Override
    public String getPhone()
    {
        return "4152876341";
    }

    @Override
    public String getKey()
    {
        return key;
    }

    @Override
    public int getBillCycleDay()
    {
        return 1;
    }

    @Override
    public Currency getCurrency()
    {
        return Currency.USD;
    }

    @Override
    public UUID getId()
    {
        return UUID.randomUUID();
    }

    @Override
    public void load()
    {
    }

    @Override
    public void save()
    {
    }

    @Override
    public String getFieldValue(final String fieldName)
    {
        return null;
    }

    @Override
    public void setFieldValue(final String fieldName, final String fieldValue)
    {
    }
}
