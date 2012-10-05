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

package com.ning.billing.junction.plumbing.api;

import java.util.UUID;

import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.junction.BlockingApi;

public class BlockingAccount implements Account {
    private final Account account;
    private final InternalTenantContext context;
    private BlockingState blockingState = null;
    private final BlockingApi blockingApi;

    public BlockingAccount(final Account account, final BlockingApi blockingApi, final InternalTenantContext context) {
        this.account = account;
        this.blockingApi = blockingApi;
        this.context = context;
    }

    @Override
    public UUID getId() {
        return account.getId();
    }

    @Override
    public String getExternalKey() {
        return account.getExternalKey();
    }

    @Override
    public String getName() {
        return account.getName();
    }

    @Override
    public Integer getFirstNameLength() {
        return account.getFirstNameLength();
    }

    @Override
    public String getEmail() {
        return account.getEmail();
    }

    @Override
    public BillCycleDay getBillCycleDay() {
        return account.getBillCycleDay();
    }

    @Override
    public Currency getCurrency() {
        return account.getCurrency();
    }

    @Override
    public UUID getPaymentMethodId() {
        return account.getPaymentMethodId();
    }

    @Override
    public MutableAccountData toMutableAccountData() {
        return account.toMutableAccountData();
    }

    @Override
    public DateTimeZone getTimeZone() {
        return account.getTimeZone();
    }

    @Override
    public String getLocale() {
        return account.getLocale();
    }

    @Override
    public BlockingState getBlockingState() {
        if (blockingState == null) {
            blockingState = blockingApi.getBlockingStateFor(account, context);
        }
        return blockingState;
    }

    @Override
    public String getAddress1() {
        return account.getAddress1();
    }

    @Override
    public String getAddress2() {
        return account.getAddress2();
    }

    @Override
    public String getCompanyName() {
        return account.getCompanyName();
    }

    @Override
    public String getCity() {
        return account.getCity();
    }

    @Override
    public String getStateOrProvince() {
        return account.getStateOrProvince();
    }

    @Override
    public String getPostalCode() {
        return account.getPostalCode();
    }

    @Override
    public String getCountry() {
        return account.getCountry();
    }

    @Override
    public String getPhone() {
        return account.getPhone();
    }

    @Override
    public Boolean isMigrated() {
        return account.isMigrated();
    }

    @Override
    public Boolean isNotifiedForInvoices() {
        return account.isNotifiedForInvoices();
    }

    @Override
    public Account mergeWithDelegate(final Account delegate) {
        return account.mergeWithDelegate(delegate);
    }
}
