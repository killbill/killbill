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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.blocking.DefaultBlockingApi;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.junction.dao.BlockingStateSqlDao;
import com.ning.billing.junction.plumbing.api.BlockingAccountUserApi;
import com.ning.billing.junction.plumbing.billing.DefaultBillingApi;

public class JunctionModule extends AbstractModule {

    @Override
    protected void configure() {
        installBlockingApi();
        installAccountUserApi();
        installBlockingStateDao();
        installBillingApi();
    }

    protected void installBillingApi() {
        bind(BillingApi.class).to(DefaultBillingApi.class).asEagerSingleton();
    }
    
    protected void installBlockingStateDao() {
        bind(BlockingStateDao.class).toProvider(BlockingDaoProvider.class);
    }
    
    protected void installAccountUserApi() {
        bind(AccountUserApi.class).to(BlockingAccountUserApi.class).asEagerSingleton();
    }
    
    protected void installBlockingApi() {
        bind(BlockingApi.class).to(DefaultBlockingApi.class).asEagerSingleton();
    }
    
    public static class BlockingDaoProvider implements Provider<BlockingStateDao>{        
        private IDBI dbi;


        @Inject
        public BlockingDaoProvider(IDBI dbi){
            this.dbi = dbi;
        }
        @Override
        public BlockingStateDao get() {
            return dbi.onDemand(BlockingStateSqlDao.class);
        }   
    }
}
