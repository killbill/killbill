/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing;

import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.callcontext.MutableCallContext;
import org.killbill.billing.callcontext.MutableInternalCallContext;
import org.killbill.billing.dao.MockNonEntityDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.clock.Clock;
import org.mockito.Mockito;

public class GuicyKillbillTestSuiteNoDB extends GuicyKillbillTestSuite {

    public static Account createMockAccount(final AccountData accountData,
                                            final AccountUserApi accountUserApi,
                                            final AccountInternalApi accountInternalApi,
                                            final ImmutableAccountInternalApi immutableAccountInternalApi,
                                            final NonEntityDao nonEntityDao,
                                            final Clock clock,
                                            final InternalCallContextFactory internalCallContextFactory,
                                            final MutableCallContext callContext,
                                            final MutableInternalCallContext internalCallContext) throws AccountApiException {
        final Account account = accountUserApi.createAccount(accountData, callContext);

        Mockito.when(accountInternalApi.getAccountById(Mockito.<UUID>eq(account.getId()), Mockito.<InternalTenantContext>any())).thenReturn(account);
        Mockito.when(accountInternalApi.getAccountByRecordId(Mockito.<Long>eq(internalCallContext.getAccountRecordId()), Mockito.<InternalTenantContext>any())).thenReturn(account);
        Mockito.when(accountInternalApi.getAccountByKey(Mockito.<String>eq(account.getExternalKey()), Mockito.<InternalTenantContext>any())).thenReturn(account);
        Mockito.when(immutableAccountInternalApi.getImmutableAccountDataByRecordId(Mockito.<Long>eq(internalCallContext.getAccountRecordId()), Mockito.<InternalTenantContext>any())).thenReturn(account);

        ((MockNonEntityDao) nonEntityDao).addTenantRecordIdMapping(account.getId(), internalCallContext);
        ((MockNonEntityDao) nonEntityDao).addAccountRecordIdMapping(account.getId(), internalCallContext);

        refreshCallContext(account.getId(), clock, internalCallContextFactory, callContext, internalCallContext);

        return account;
    }
}
