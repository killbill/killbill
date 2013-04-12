/*
 * Copyright 2010-2013 Ning, Inc.
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

import org.skife.config.ConfigSource;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.tweak.TransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.analytics.AnalyticsListener;
import com.ning.billing.analytics.BusinessAccountDao;
import com.ning.billing.analytics.BusinessSubscriptionTransitionDao;
import com.ning.billing.analytics.BusinessTagDao;
import com.ning.billing.analytics.api.AnalyticsService;
import com.ning.billing.analytics.api.DefaultAnalyticsService;
import com.ning.billing.analytics.api.sanity.AnalyticsSanityApi;
import com.ning.billing.analytics.api.sanity.DefaultAnalyticsSanityApi;
import com.ning.billing.analytics.api.user.AnalyticsUserApi;
import com.ning.billing.analytics.api.user.DefaultAnalyticsUserApi;
import com.ning.billing.analytics.dao.AnalyticsDao;
import com.ning.billing.analytics.dao.AnalyticsSanityDao;
import com.ning.billing.analytics.dao.BusinessAccountFieldSqlDao;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.dao.BusinessAccountTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceFieldSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceItemSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentFieldSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceTagSqlDao;
import com.ning.billing.analytics.dao.BusinessOverdueStatusSqlDao;
import com.ning.billing.analytics.dao.BusinessSqlProvider;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionFieldSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionTagSqlDao;
import com.ning.billing.analytics.dao.DefaultAnalyticsDao;
import com.ning.billing.analytics.dao.DefaultAnalyticsSanityDao;
import com.ning.billing.util.dao.DateTimeArgumentFactory;
import com.ning.billing.util.dao.DateTimeZoneArgumentFactory;
import com.ning.billing.util.dao.EnumArgumentFactory;
import com.ning.billing.util.dao.LocalDateArgumentFactory;
import com.ning.billing.util.dao.UUIDArgumentFactory;
import com.ning.billing.util.dao.UuidMapper;

import com.google.inject.AbstractModule;
import com.mchange.v2.c3p0.ComboPooledDataSource;

public class AnalyticsModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsModule.class);

    public static final String ANALYTICS_DBI_CONFIG_STRING = "com.ning.billing.analytics.dbi.";

    protected final ConfigSource configSource;
    protected final DBI dbi;

    public AnalyticsModule(final ConfigSource configSource) {
        this.configSource = configSource;
        this.dbi = createDBI(configSource);
    }

    @Override
    protected void configure() {
        installAnalyticsUserApi();
        installAnalyticsSanityApi();

        installAnalyticsDao();
        installAnalyticsSqlDao();

        bind(AnalyticsListener.class).asEagerSingleton();
        bind(AnalyticsService.class).to(DefaultAnalyticsService.class).asEagerSingleton();
    }

    protected void installAnalyticsUserApi() {
        bind(DefaultAnalyticsUserApi.class).asEagerSingleton();
        bind(AnalyticsUserApi.class).to(DefaultAnalyticsUserApi.class).asEagerSingleton();
    }

    protected void installAnalyticsSanityApi() {
        bind(AnalyticsSanityApi.class).to(DefaultAnalyticsSanityApi.class).asEagerSingleton();
    }

    protected void installAnalyticsDao() {
        bind(AnalyticsDao.class).to(DefaultAnalyticsDao.class).asEagerSingleton();
        bind(AnalyticsSanityDao.class).toInstance(new DefaultAnalyticsSanityDao(dbi));
        bind(BusinessSubscriptionTransitionDao.class).asEagerSingleton();
        bind(BusinessAccountDao.class).asEagerSingleton();
        bind(BusinessTagDao.class).asEagerSingleton();
    }

    protected void installAnalyticsSqlDao() {
        bind(BusinessAccountSqlDao.class).toProvider(new BusinessSqlProvider<BusinessAccountSqlDao>(dbi, BusinessAccountSqlDao.class));
        bind(BusinessAccountTagSqlDao.class).toProvider(new BusinessSqlProvider<BusinessAccountTagSqlDao>(dbi, BusinessAccountTagSqlDao.class));
        bind(BusinessAccountFieldSqlDao.class).toProvider(new BusinessSqlProvider<BusinessAccountFieldSqlDao>(dbi, BusinessAccountFieldSqlDao.class));
        bind(BusinessInvoiceFieldSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoiceFieldSqlDao>(dbi, BusinessInvoiceFieldSqlDao.class));
        bind(BusinessInvoiceItemSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoiceItemSqlDao>(dbi, BusinessInvoiceItemSqlDao.class));
        bind(BusinessInvoicePaymentFieldSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoicePaymentFieldSqlDao>(dbi, BusinessInvoicePaymentFieldSqlDao.class));
        bind(BusinessInvoicePaymentSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoicePaymentSqlDao>(dbi, BusinessInvoicePaymentSqlDao.class));
        bind(BusinessInvoicePaymentTagSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoicePaymentTagSqlDao>(dbi, BusinessInvoicePaymentTagSqlDao.class));
        bind(BusinessInvoiceSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoiceSqlDao>(dbi, BusinessInvoiceSqlDao.class));
        bind(BusinessInvoiceTagSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoiceTagSqlDao>(dbi, BusinessInvoiceTagSqlDao.class));
        bind(BusinessOverdueStatusSqlDao.class).toProvider(new BusinessSqlProvider<BusinessOverdueStatusSqlDao>(dbi, BusinessOverdueStatusSqlDao.class));
        bind(BusinessSubscriptionTransitionFieldSqlDao.class).toProvider(new BusinessSqlProvider<BusinessSubscriptionTransitionFieldSqlDao>(dbi, BusinessSubscriptionTransitionFieldSqlDao.class));
        bind(BusinessSubscriptionTransitionSqlDao.class).toProvider(new BusinessSqlProvider<BusinessSubscriptionTransitionSqlDao>(dbi, BusinessSubscriptionTransitionSqlDao.class));
        bind(BusinessSubscriptionTransitionTagSqlDao.class).toProvider(new BusinessSqlProvider<BusinessSubscriptionTransitionTagSqlDao>(dbi, BusinessSubscriptionTransitionTagSqlDao.class));
    }

    private DBI createDBI(final ConfigSource configSource) {
        final ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setJdbcUrl(configSource.getString(ANALYTICS_DBI_CONFIG_STRING + "url"));
        dataSource.setUser(configSource.getString(ANALYTICS_DBI_CONFIG_STRING + "user"));
        dataSource.setPassword(configSource.getString(ANALYTICS_DBI_CONFIG_STRING + "password"));
        dataSource.setMinPoolSize(1);
        dataSource.setMaxPoolSize(10);
        dataSource.setCheckoutTimeout(10 * 1000);
        dataSource.setMaxIdleTime(60 * 60);
        dataSource.setMaxConnectionAge(0);
        dataSource.setIdleConnectionTestPeriod(5 * 60);

        final DBI dbi = new DBI(dataSource);
        dbi.registerArgumentFactory(new UUIDArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeZoneArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeArgumentFactory());
        dbi.registerArgumentFactory(new LocalDateArgumentFactory());
        dbi.registerArgumentFactory(new EnumArgumentFactory());
        dbi.registerMapper(new UuidMapper());
        try {
            dbi.setTransactionHandler((TransactionHandler) Class.forName("com.ning.jetty.jdbi.RestartTransactionRunner").newInstance());
        } catch (Exception e) {
            logger.warn("Unable to register transaction handler");
        }

        return dbi;
    }
}
