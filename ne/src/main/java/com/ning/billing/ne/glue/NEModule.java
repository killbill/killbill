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

package com.ning.billing.ne.glue;

import org.skife.jdbi.v2.IDBI;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.ning.billing.ne.override.dao.OverrideStateDao;
import com.ning.billing.ne.override.dao.OverrideStateSqlDao;
import com.ning.billing.overdue.OverdueAccessApi;
import com.ning.billing.util.overdue.DefaultOverdueAcessApi;

public class NEModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(OverdueAccessApi.class).to(DefaultOverdueAcessApi.class);
        bind(OverrideStateDao.class).toProvider(OverdueDaoProvider.class);
    }

    public static class OverdueDaoProvider implements Provider<OverrideStateDao>{
        
        private IDBI dbi;


        @Inject
        public OverdueDaoProvider(IDBI dbi){
            this.dbi = dbi;
        }
        

        @Override
        public OverrideStateDao get() {
            return dbi.onDemand(OverrideStateSqlDao.class);
        }   
    }
}
