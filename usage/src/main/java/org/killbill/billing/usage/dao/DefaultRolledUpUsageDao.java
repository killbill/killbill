/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.usage.dao;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.entity.dao.DBRouter;
import org.skife.jdbi.v2.IDBI;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultRolledUpUsageDao implements RolledUpUsageDao {

    private final DBRouter<RolledUpUsageSqlDao> dbRouter;

    @Inject
    public DefaultRolledUpUsageDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi) {
        this.dbRouter = new DBRouter<RolledUpUsageSqlDao>(dbi, roDbi, RolledUpUsageSqlDao.class);
    }

    @Override
    public void record(final Iterable<RolledUpUsageModelDao> usages, final InternalCallContext context) {
        dbRouter.onDemand(false).create(usages, context);
    }

    @Override
    public Boolean recordsWithTrackingIdExist(final UUID subscriptionId, final String trackingId, final InternalTenantContext context) {
        return dbRouter.onDemand(false).recordsWithTrackingIdExist(subscriptionId, trackingId, context) != null;
    }

    @Override
    public List<RolledUpUsageModelDao> getUsageForSubscription(final UUID subscriptionId, final LocalDate startDate, final LocalDate endDate, final String unitType, final InternalTenantContext context) {
        return dbRouter.onDemand(true).getUsageForSubscription(subscriptionId, startDate.toDate(), endDate.toDate(), unitType, context);
    }

    @Override
    public List<RolledUpUsageModelDao> getAllUsageForSubscription(final UUID subscriptionId, final LocalDate startDate, final LocalDate endDate, final InternalTenantContext context) {
        return dbRouter.onDemand(true).getAllUsageForSubscription(subscriptionId, startDate.toDate(), endDate.toDate(), context);
    }

    @Override
    public List<RolledUpUsageModelDao> getRawUsageForAccount(final LocalDate startDate, final LocalDate endDate, final InternalTenantContext context) {
        return dbRouter.onDemand(true).getRawUsageForAccount(startDate.toDate(), endDate.toDate(), context);
    }
}
