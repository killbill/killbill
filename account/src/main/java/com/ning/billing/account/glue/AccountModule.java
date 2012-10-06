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

package com.ning.billing.account.glue;

import com.ning.billing.account.api.AccountService;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.DefaultAccountService;
import com.ning.billing.account.api.svcs.DefaultAccountInternalApi;
import com.ning.billing.account.api.user.DefaultAccountUserApi;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountEmailDao;
import com.ning.billing.account.dao.AuditedAccountDao;
import com.ning.billing.account.dao.AuditedAccountEmailDao;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.account.AccountInternalApi;

import com.google.inject.AbstractModule;

public class AccountModule extends AbstractModule {
    private void installConfig() {
    }

    protected void installAccountDao() {
        bind(AccountEmailDao.class).to(AuditedAccountEmailDao.class).asEagerSingleton();
        bind(AccountDao.class).to(AuditedAccountDao.class).asEagerSingleton();
    }

    protected void installAccountUserApi() {
        bind(AccountUserApi.class).annotatedWith(RealImplementation.class).to(DefaultAccountUserApi.class).asEagerSingleton();
        bind(AccountInternalApi.class).annotatedWith(RealImplementation.class).to(DefaultAccountInternalApi.class).asEagerSingleton();
    }

    private void installAccountService() {
        bind(AccountService.class).to(DefaultAccountService.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();
        installAccountDao();
        installAccountService();
        installAccountUserApi();
    }
}
