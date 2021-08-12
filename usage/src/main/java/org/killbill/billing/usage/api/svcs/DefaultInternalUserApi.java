/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.usage.api.svcs;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.DryRunInfo;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.usage.DryRunTenantContext;
import org.killbill.billing.usage.InternalUserApi;
import org.killbill.billing.usage.api.BaseUserApi;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.usage.dao.RolledUpUsageDao;
import org.killbill.billing.usage.dao.RolledUpUsageModelDao;
import org.killbill.billing.usage.plugin.api.UsagePluginApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DefaultInternalUserApi extends BaseUserApi implements InternalUserApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInternalUserApi.class);

    private final RolledUpUsageDao rolledUpUsageDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultInternalUserApi(final RolledUpUsageDao rolledUpUsageDao,
                                  final InternalCallContextFactory internalCallContextFactory,
                                  final OSGIServiceRegistration<UsagePluginApi> pluginRegistry) {
        super(pluginRegistry);
        this.rolledUpUsageDao = rolledUpUsageDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public List<RawUsageRecord> getRawUsageForAccount(final LocalDate startDate, final LocalDate endDate, @Nullable final DryRunInfo dryRunInfo, final InternalTenantContext internalTenantContext) {

        log.info("GetRawUsageForAccount startDate='{}', endDate='{}'", startDate, endDate);

        final TenantContext tenantContext = internalCallContextFactory.createTenantContext(internalTenantContext);
        final TenantContext tenantContextWithHint = dryRunInfo != null ? new DryRunTenantContext(dryRunInfo, tenantContext) : tenantContext;
        final List<RawUsageRecord> resultFromPlugin = getAccountUsageFromPlugin(startDate, endDate, tenantContextWithHint);
        if (resultFromPlugin != null) {
            return resultFromPlugin;
        }

        final List<RolledUpUsageModelDao> usage = rolledUpUsageDao.getRawUsageForAccount(startDate, endDate, internalTenantContext);
        return ImmutableList.copyOf(Iterables.transform(usage, new Function<RolledUpUsageModelDao, RawUsageRecord>() {
            @Override
            public RawUsageRecord apply(final RolledUpUsageModelDao input) {
                return new DefaultRawUsage(input.getSubscriptionId(), input.getRecordDate(), input.getUnitType(), input.getAmount(), input.getTrackingId());
            }
        }));
    }

}
