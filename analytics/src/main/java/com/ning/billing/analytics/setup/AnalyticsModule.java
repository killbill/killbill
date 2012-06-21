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

package com.ning.billing.analytics.setup;


import com.google.inject.AbstractModule;
import com.ning.billing.analytics.AnalyticsListener;
import com.ning.billing.analytics.BusinessAccountRecorder;
import com.ning.billing.analytics.BusinessSubscriptionTransitionRecorder;
import com.ning.billing.analytics.api.AnalyticsService;
import com.ning.billing.analytics.api.DefaultAnalyticsService;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.dao.BusinessAccountSqlDaoProvider;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDaoProvider;

public class AnalyticsModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(BusinessSubscriptionTransitionSqlDao.class).toProvider(BusinessSubscriptionTransitionSqlDaoProvider.class).asEagerSingleton();
        bind(BusinessAccountSqlDao.class).toProvider(BusinessAccountSqlDaoProvider.class).asEagerSingleton();

        bind(BusinessSubscriptionTransitionRecorder.class).asEagerSingleton();
        bind(BusinessAccountRecorder.class).asEagerSingleton();
        bind(AnalyticsListener.class).asEagerSingleton();

        bind(AnalyticsService.class).to(DefaultAnalyticsService.class).asEagerSingleton();
    }
}
