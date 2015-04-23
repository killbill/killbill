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
import org.killbill.billing.usage.InternalUserApi;
import org.killbill.billing.usage.RawUsage;
import org.killbill.billing.usage.dao.RolledUpUsageDao;
import org.killbill.billing.usage.dao.RolledUpUsageModelDao;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DefaultInternalUserApi implements InternalUserApi {

    private final RolledUpUsageDao rolledUpUsageDao;

    @Inject
    public DefaultInternalUserApi(final RolledUpUsageDao rolledUpUsageDao) {
        this.rolledUpUsageDao = rolledUpUsageDao;
    }

    @Override
    public List<RawUsage> getRawUsageForAccount(final LocalDate stateDate, final LocalDate endDate, final InternalTenantContext internalTenantContext) {
        final List<RolledUpUsageModelDao> usage = rolledUpUsageDao.getRawUsageForAccount(stateDate, endDate, internalTenantContext);
        return ImmutableList.copyOf(Iterables.transform(usage, new Function<RolledUpUsageModelDao, RawUsage>() {
            @Nullable
            @Override
            public RawUsage apply(final RolledUpUsageModelDao input) {
                return new DefaultRawUsage(input.getSubscriptionId(), input.getRecordDate(), input.getUnitType(), input.getAmount());
            }
        }));
    }
}
