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

package com.ning.billing.osgi.bundles.analytics.dao;

import javax.sql.DataSource;

import org.skife.jdbi.v2.Binding;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;

import com.ning.billing.commons.jdbi.ReusableStringTemplate3StatementLocator;
import com.ning.billing.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessBundleSummaryModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceAdjustmentModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemAdjustmentModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemCreditModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentChargebackModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentRefundModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;

import com.google.common.base.CaseFormat;

public class BusinessDBIProvider {

    private BusinessDBIProvider() {}

    public static DBI get(final DataSource dataSource) {
        final DBI dbi = new DBI(dataSource);

        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessAccountFieldModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessAccountModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessAccountTagModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoiceAdjustmentModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoiceFieldModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoiceItemAdjustmentModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoiceItemCreditModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoiceItemModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoiceModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoicePaymentChargebackModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoicePaymentFieldModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoicePaymentModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoicePaymentRefundModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoicePaymentTagModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessInvoiceTagModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessOverdueStatusModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessSubscriptionTransitionModelDao.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusinessBundleSummaryModelDao.class));

        dbi.setStatementLocator(new AnalyticsStatementLocator());

        return dbi;
    }

    private static final class AnalyticsStatementLocator extends ReusableStringTemplate3StatementLocator {

        public AnalyticsStatementLocator() {
            super(BusinessAnalyticsSqlDao.class, true, true);
        }

        @Override
        public String locate(final String name, final StatementContext ctx) throws Exception {
            // Rewrite create to createBac, createBin, createBiia, etc.
            if ("create".equals(name)) {
                final Binding binding = ctx.getBinding();
                if (binding != null) {
                    final Argument tableNameArgument = binding.forName("tableName");
                    if (tableNameArgument != null) {
                        // Lame, rely on toString
                        final String tableName = tableNameArgument.toString();
                        final String newQueryName = name + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, tableName);
                        return super.locate(newQueryName, ctx);
                    }
                }
            }
            return super.locate(name, ctx);
        }
    }
}
