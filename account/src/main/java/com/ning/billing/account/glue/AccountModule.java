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

import org.skife.config.ConfigurationObjectFactory;

import com.google.inject.AbstractModule;
import com.ning.billing.account.api.AccountService;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.IAccountService;
import com.ning.billing.account.api.IAccountUserApi;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.FieldStoreDao;
import com.ning.billing.account.dao.IAccountDao;
import com.ning.billing.account.dao.IFieldStoreDao;

public class AccountModule extends AbstractModule {

    protected void installConfig() {
        final IAccountConfig config = new ConfigurationObjectFactory(System.getProperties()).build(IAccountConfig.class);
        bind(IAccountConfig.class).toInstance(config);
    }

    protected void installAccountDao() {
        bind(IAccountDao.class).to(AccountDao.class).asEagerSingleton();
//        bind(IAccountDaoSql.class).to(IAccountDaoSql.class).asEagerSingleton();
    }

    protected void installAccountUserApi() {
        bind(IAccountUserApi.class).to(AccountUserApi.class).asEagerSingleton();
    }

    protected void installAccountService() {
        bind(IAccountService.class).to(AccountService.class).asEagerSingleton();
    }

    protected void installFieldStore() {
        bind(IFieldStoreDao.class).to(FieldStoreDao.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();
        installAccountDao();
        installAccountUserApi();
        installAccountService();
        installFieldStore();
    }

}
