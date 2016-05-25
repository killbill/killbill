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

package org.killbill.billing.usage.dao;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.skife.jdbi.v2.IDBI;

public class DefaultRolledUpUsageDao implements RolledUpUsageDao {

    private final RolledUpUsageSqlDao rolledUpUsageSqlDao;

    @Inject
    public DefaultRolledUpUsageDao(final IDBI dbi) {
        this.rolledUpUsageSqlDao = dbi.onDemand(RolledUpUsageSqlDao.class);
    }

    @Override
    public void record(final Iterable<RolledUpUsageModelDao> usages, final InternalCallContext context){
        rolledUpUsageSqlDao.create(usages, context);
    }

    @Override
    public Boolean recordsWithTrackingIdExist(final UUID subscriptionId, final String trackingId, final InternalTenantContext context){
        return rolledUpUsageSqlDao.recordsWithTrackingIdExist(subscriptionId, trackingId, context) != null ;
    }

    @Override
    public List<RolledUpUsageModelDao> getUsageForSubscription(final UUID subscriptionId, final LocalDate startDate, final LocalDate endDate, final String unitType, final InternalTenantContext context) {
        return rolledUpUsageSqlDao.getUsageForSubscription(subscriptionId, startDate.toDate(), endDate.toDate(), unitType, context);
    }

    @Override
    public List<RolledUpUsageModelDao> getAllUsageForSubscription(final UUID subscriptionId, final LocalDate startDate, final LocalDate endDate, final InternalTenantContext context) {
        return rolledUpUsageSqlDao.getAllUsageForSubscription(subscriptionId, startDate.toDate(), endDate.toDate(), context);
    }

    @Override
    public List<RolledUpUsageModelDao> getRawUsageForAccount(final LocalDate startDate, final LocalDate endDate, final InternalTenantContext context) {
        return rolledUpUsageSqlDao.getRawUsageForAccount(startDate.toDate(), endDate.toDate(), context);
    }
}
