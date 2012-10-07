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
package com.ning.billing.mock.glue;

import org.mockito.Mockito;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.glue.AccountModule;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.account.AccountInternalApi;

import com.google.inject.AbstractModule;

public class MockAccountModule extends AbstractModule implements AccountModule {

    @Override
    protected void configure() {
        installAccountUserApi();
        installInternalApi();
    }


    @Override
    public void installAccountUserApi() {
        bind(AccountUserApi.class).annotatedWith(RealImplementation.class).toInstance(Mockito.mock(AccountUserApi.class));
    }

    @Override
    public void installInternalApi() {
        bind(AccountInternalApi.class).toInstance(Mockito.mock(AccountInternalApi.class));
    }

}
