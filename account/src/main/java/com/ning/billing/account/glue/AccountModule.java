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

import com.google.inject.AbstractModule;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.user.DefaultAccountUserApi;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountDaoWrapper;
import org.skife.config.ConfigurationObjectFactory;

public class AccountModule extends AbstractModule {

    private void installConfig() {
        final AccountConfig config = new ConfigurationObjectFactory(System.getProperties()).build(AccountConfig.class);
        bind(AccountConfig.class).toInstance(config);
    }

    private void installAccountCore() {
//        bind(IAccountService.class).to(Engine.class).asEagerSingleton();
//        bind(Engine.class).asEagerSingleton();
    }

    private void installAccountDao() {
        bind(AccountDao.class).to(AccountDaoWrapper.class).asEagerSingleton();
    }

    private void installAccountUserApi() {
        bind(AccountUserApi.class).to(DefaultAccountUserApi.class).asEagerSingleton();
    }

//    private void installFieldStore() {
//        bind(FieldStoreDao.class).to(FieldStoreDaoWrapper.class).asEagerSingleton();
//    }
//
//    private void installTagStore() {
//        bind(TagStoreDao.class).to(TagStoreDaoWrapper.class).asEagerSingleton();
//    }

    @Override
    protected void configure() {
        installConfig();
        installAccountCore();
        installAccountDao();
        installAccountUserApi();
//        installFieldStore();
//        installTagStore();
    }
}
