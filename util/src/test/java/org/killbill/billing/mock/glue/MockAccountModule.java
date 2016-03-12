/*
 * Copyright 2010-2011 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.mock.glue;

import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.glue.AccountModule;
import org.killbill.billing.mock.api.MockAccountUserApi;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.KillBillModule;
import org.mockito.Mockito;

public class MockAccountModule extends KillBillModule implements AccountModule {

    public MockAccountModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        installAccountUserApi();
        installInternalApi();
    }

    @Override
    public void installAccountUserApi() {
        bind(AccountUserApi.class).toInstance(new MockAccountUserApi());
    }

    @Override
    public void installInternalApi() {
        final ImmutableAccountData immutableAccountData = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(immutableAccountData.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(immutableAccountData.getFixedOffsetTimeZone()).thenReturn(DateTimeZone.UTC);

        final AccountInternalApi accountInternalApi = Mockito.mock(AccountInternalApi.class);
        final ImmutableAccountInternalApi immutableAccountInternalApi = Mockito.mock(ImmutableAccountInternalApi.class);
        bind(AccountInternalApi.class).toInstance(accountInternalApi);
        bind(ImmutableAccountInternalApi.class).toInstance(immutableAccountInternalApi);
    }
}
