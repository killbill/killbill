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
    private final UUID id;
    private final String accountKey;
    private final Currency currency;

    public MockAccount(final UUID id, final String accountKey, final Currency currency)
    {
        this.id = id;
        this.accountKey = accountKey;
        this.currency = currency;
    }

    @Override
    public String getName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getEmail()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPhone()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getExternalKey()
    {
        return accountKey;
    }

    @Override
    public int getBillCycleDay()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Currency getCurrency()
    {
        return currency;
    }

    @Override
    public UUID getId()
    {
        return id;
    }

    @Override
    public boolean isNew() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAsSaved() {
        throw new UnsupportedOperationException();
    }
}
