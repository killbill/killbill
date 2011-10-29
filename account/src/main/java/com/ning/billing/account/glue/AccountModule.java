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
import org.skife.jdbi.v2.DBI;

import com.google.inject.AbstractModule;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.IAccountDao;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DbiConfig;

public class AccountModule extends AbstractModule {

    protected void installConfig() {
        final IAccountConfig config = new ConfigurationObjectFactory(System.getProperties()).build(IAccountConfig.class);
        bind(IAccountConfig.class).toInstance(config);
    }
    protected void installDBI() {
        bind(DBI.class).toProvider(DBIProvider.class).asEagerSingleton();
        final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
        bind(DbiConfig.class).toInstance(config);
    }

    protected void installAccountDao() {
        bind(IAccountDao.class).to(AccountDao.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();
        installDBI();
        installAccountDao();
    }

}
