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

package org.killbill.billing.usage.api.user;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.usage.api.BaseUserApi;
import org.killbill.billing.usage.api.DefaultUsageContext;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.SubscriptionUsageRecord;
import org.killbill.billing.usage.api.UnitUsageRecord;
import org.killbill.billing.usage.api.UsageApiException;
import org.killbill.billing.usage.api.UsageRecord;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.usage.dao.RolledUpUsageDao;
import org.killbill.billing.usage.dao.RolledUpUsageModelDao;
import org.killbill.billing.usage.plugin.api.UsageContext;
import org.killbill.billing.usage.plugin.api.UsagePluginApi;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

public class DefaultUsageUserApi extends BaseUserApi implements UsageUserApi {

    private final RolledUpUsageDao rolledUpUsageDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultUsageUserApi(final RolledUpUsageDao rolledUpUsageDao,
                               final InternalCallContextFactory internalCallContextFactory,
                               final OSGIServiceRegistration<UsagePluginApi> pluginRegistry) {
        super(pluginRegistry);
        this.rolledUpUsageDao = rolledUpUsageDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void recordRolledUpUsage(final SubscriptionUsageRecord record, final CallContext callContext) throws UsageApiException {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(record.getSubscriptionId(), ObjectType.SUBSCRIPTION, callContext);

        final String trackingIds;
        if (record.getTrackingId() == null || record.getTrackingId().isEmpty()) {
            trackingIds = UUIDs.randomUUID().toString();
        // check if we have (at least) one row with the supplied tracking id
        } else if (recordsWithTrackingIdExist(record, internalCallContext)) {
            throw new UsageApiException(ErrorCode.USAGE_RECORD_TRACKING_ID_ALREADY_EXISTS, record.getTrackingId());
        } else {
            trackingIds = record.getTrackingId();
        }


        final List<RolledUpUsageModelDao> usages = new ArrayList<>();
        for (final UnitUsageRecord unitUsageRecord : record.getUnitUsageRecord()) {
            for (final UsageRecord usageRecord : unitUsageRecord.getDailyAmount()) {
                usages.add(new RolledUpUsageModelDao(record.getSubscriptionId(), unitUsageRecord.getUnitType(), usageRecord.getDate(), usageRecord.getAmount(), trackingIds));
            }
        }
        rolledUpUsageDao.record(usages, internalCallContext);
    }

    @Override
    public RolledUpUsage getUsageForSubscription(final UUID subscriptionId, final String unitType, final DateTime startDate, final DateTime endDate, final Iterable<PluginProperty> properties, final TenantContext tenantContextNoAccountId) {
        final InternalTenantContext internalCallContext = internalCallContextFactory.createInternalTenantContext(subscriptionId, ObjectType.SUBSCRIPTION, tenantContextNoAccountId);
        final TenantContext tenantContext = internalCallContextFactory.createTenantContext(internalCallContext);
        final UsageContext usageContext = new DefaultUsageContext(null, null, tenantContext);
        final List<RawUsageRecord> rawUsage = getSubscriptionUsageFromPlugin(subscriptionId, startDate, endDate, properties, usageContext);
        if (rawUsage != null) {
            final List<RolledUpUnit> rolledUpAmount = getRolledUpUnitsForRawPluginUsage(subscriptionId, unitType, rawUsage);
            return new DefaultRolledUpUsage(subscriptionId, startDate, endDate, rolledUpAmount);
        }

        final List<RolledUpUsageModelDao> usageForSubscription = rolledUpUsageDao.getUsageForSubscription(subscriptionId, startDate, endDate, unitType, internalCallContextFactory.createInternalTenantContext(subscriptionId, ObjectType.SUBSCRIPTION, tenantContext));
        final List<RolledUpUnit> rolledUpAmount = getRolledUpUnits(usageForSubscription);
        return new DefaultRolledUpUsage(subscriptionId, startDate, endDate, rolledUpAmount);
    }

    @Override
    public List<RolledUpUsage> getAllUsageForSubscription(final UUID subscriptionId, final List<DateTime> transitionTimes, final Iterable<PluginProperty> properties, final TenantContext tenantContextNoAccountId) {
        final InternalTenantContext internalCallContext = internalCallContextFactory.createInternalTenantContext(subscriptionId, ObjectType.SUBSCRIPTION, tenantContextNoAccountId);
        final TenantContext tenantContext = internalCallContextFactory.createTenantContext(internalCallContext);
        final UsageContext usageContext = new DefaultUsageContext(null, null, tenantContext);

        final List<RolledUpUsage> result = new ArrayList<RolledUpUsage>();
        DateTime prevDate = null;
        for (final DateTime curDate : transitionTimes) {
            if (prevDate != null) {

                final List<RawUsageRecord> rawUsage = getSubscriptionUsageFromPlugin(subscriptionId, prevDate, curDate, properties, usageContext);
                if (rawUsage != null) {
                    final List<RolledUpUnit> rolledUpAmount = getRolledUpUnitsForRawPluginUsage(subscriptionId, null, rawUsage);
                    result.add(new DefaultRolledUpUsage(subscriptionId, prevDate, curDate, rolledUpAmount));
                } else {
                    final List<RolledUpUsageModelDao> usageForSubscription = rolledUpUsageDao.getAllUsageForSubscription(subscriptionId, prevDate, curDate, internalCallContext);
                    final List<RolledUpUnit> rolledUpAmount = getRolledUpUnits(usageForSubscription);
                    result.add(new DefaultRolledUpUsage(subscriptionId, prevDate, curDate, rolledUpAmount));
                }
            }
            prevDate = curDate;
        }
        return result;
    }

    private List<RolledUpUnit> getRolledUpUnitsForRawPluginUsage(final UUID subscriptionId, @Nullable final String unitType, final List<RawUsageRecord> rawAccountUsage) {
        final Map<String, BigDecimal> tmp = new HashMap<>();
        for (final RawUsageRecord cur : rawAccountUsage) {
            // Filter out wrong subscriptionId
            if (cur.getSubscriptionId().compareTo(subscriptionId) != 0) {
                continue;
            }

            // Filter out wrong unitType if specified.
            if (unitType != null && !unitType.equals(cur.getUnitType())) {
                continue;
            }

            final BigDecimal currentAmount = tmp.get(cur.getUnitType());
            final BigDecimal updatedAmount = (currentAmount != null) ? currentAmount.add(cur.getAmount()) : cur.getAmount();
            tmp.put(cur.getUnitType(), updatedAmount);
        }
        final List<RolledUpUnit> result = new ArrayList<>(tmp.size());
        for (final String curType : tmp.keySet()) {
            result.add(new DefaultRolledUpUnit(curType, tmp.get(curType)));
        }
        return result;
    }

    private List<RolledUpUnit> getRolledUpUnits(final List<RolledUpUsageModelDao> usageForSubscription) {
        final Map<String, BigDecimal> tmp = new HashMap<>();
        for (final RolledUpUsageModelDao cur : usageForSubscription) {
            final BigDecimal currentAmount = tmp.get(cur.getUnitType());
            final BigDecimal updatedAmount = (currentAmount != null) ? currentAmount.add(cur.getAmount()) : cur.getAmount();
            tmp.put(cur.getUnitType(), updatedAmount);
        }
        final List<RolledUpUnit> result = new ArrayList<>(tmp.size());
        for (final String unitType : tmp.keySet()) {
            result.add(new DefaultRolledUpUnit(unitType, tmp.get(unitType)));
        }
        return result;
    }

    private boolean recordsWithTrackingIdExist(final SubscriptionUsageRecord record, final InternalCallContext context) {
        return rolledUpUsageDao.recordsWithTrackingIdExist(record.getSubscriptionId(), record.getTrackingId(), context);
    }
}
