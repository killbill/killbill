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

package com.ning.billing.junction.glue;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.glue.JunctionModule;
import com.ning.billing.junction.api.svcs.DefaultInternalBlockingApi;
import com.ning.billing.junction.block.BlockingChecker;
import com.ning.billing.junction.block.DefaultBlockingChecker;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.junction.dao.BlockingStateSqlDao;
import com.ning.billing.junction.dao.DefaultBlockingStateDao;
import com.ning.billing.junction.plumbing.api.BlockingAccountUserApi;
import com.ning.billing.junction.plumbing.api.BlockingEntitlementUserApi;
import com.ning.billing.junction.plumbing.billing.BlockingCalculator;
import com.ning.billing.junction.plumbing.billing.DefaultInternalBillingApi;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class DefaultJunctionModule extends AbstractModule implements JunctionModule {

    @Override
    protected void configure() {
        // External
        installBlockingApi();
        installAccountUserApi();
        installBillingApi();
        installEntitlementUserApi();
        installBlockingChecker();

        // Internal
        installBlockingCalculator();
        installBlockingStateDao();
    }

    public void installBlockingChecker() {
        bind(BlockingChecker.class).to(DefaultBlockingChecker.class).asEagerSingleton();

    }

    public void installBillingApi() {
        bind(BillingInternalApi.class).to(DefaultInternalBillingApi.class).asEagerSingleton();
    }

    public void installBlockingStateDao() {
        bind(BlockingStateDao.class).to(DefaultBlockingStateDao.class).asEagerSingleton();
    }

    public void installAccountUserApi() {
        bind(AccountUserApi.class).to(BlockingAccountUserApi.class).asEagerSingleton();
    }

    public void installEntitlementUserApi() {
        bind(EntitlementUserApi.class).to(BlockingEntitlementUserApi.class).asEagerSingleton();
    }

    public void installBlockingApi() {
        bind(BlockingInternalApi.class).to(DefaultInternalBlockingApi.class).asEagerSingleton();
    }

    public void installBlockingCalculator() {
        bind(BlockingCalculator.class).asEagerSingleton();
    }
}
