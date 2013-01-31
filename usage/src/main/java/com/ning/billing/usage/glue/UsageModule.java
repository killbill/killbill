/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.usage.glue;

import com.ning.billing.usage.api.UsageUserApi;
import com.ning.billing.usage.api.user.DefaultUsageUserApi;
import com.ning.billing.usage.dao.DefaultRolledUpUsageDao;
import com.ning.billing.usage.dao.RolledUpUsageDao;

import com.google.inject.AbstractModule;

public class UsageModule extends AbstractModule {

    protected void installRolledUpUsageDao() {
        bind(RolledUpUsageDao.class).to(DefaultRolledUpUsageDao.class).asEagerSingleton();
    }

    protected void installUsageUserApi() {
        bind(UsageUserApi.class).to(DefaultUsageUserApi.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installRolledUpUsageDao();
        installUsageUserApi();
    }
}
