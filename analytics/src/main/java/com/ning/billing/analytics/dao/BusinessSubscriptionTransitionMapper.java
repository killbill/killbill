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

package com.ning.billing.analytics.dao;

import com.ning.billing.analytics.BusinessSubscription;
import com.ning.billing.analytics.BusinessSubscriptionEvent;
import com.ning.billing.analytics.BusinessSubscriptionTransition;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.ISubscription;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class BusinessSubscriptionTransitionMapper implements ResultSetMapper<BusinessSubscriptionTransition>
{
    @Override
    public BusinessSubscriptionTransition map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException
    {
        BusinessSubscription prev = new BusinessSubscription(
            r.getString(4), // productName
            r.getString(5), // productType
            r.getString(6) == null ? null : ProductCategory.valueOf(r.getString(6)), // productCategory
            r.getString(7), // slug
            r.getString(8),  // phase
            BigDecimal.valueOf(r.getDouble(9)), // price
            BigDecimal.valueOf(r.getDouble(10)), // mrr
            r.getString(11), // currency
            r.getLong(12) == 0 ? null : new DateTime(r.getLong(12), DateTimeZone.UTC), // startDate
            r.getString(13) == null ? null : ISubscription.SubscriptionState.valueOf(r.getString(13)), // state
            r.getString(14) == null ? null : UUID.fromString(r.getString(14)), // subscriptionId
            r.getString(15) == null ? null : UUID.fromString(r.getString(15)) //bundleId
        );

        // Avoid creating a dummy subscriptions with all null fields
        if (prev.getProductName() == null && prev.getSlug() == null) {
            prev = null;
        }

        BusinessSubscription next = new BusinessSubscription(
            r.getString(16), // productName
            r.getString(17), // productType
            r.getString(18) == null ? null : ProductCategory.valueOf(r.getString(18)), // productCategory
            r.getString(19), // slug8
            r.getString(20),  // phase
            BigDecimal.valueOf(r.getDouble(21)), // price
            BigDecimal.valueOf(r.getDouble(22)), // mrr
            r.getString(23), // currency
            r.getLong(24) == 0 ? null : new DateTime(r.getLong(24), DateTimeZone.UTC), // startDate
            r.getString(25) == null ? null : ISubscription.SubscriptionState.valueOf(r.getString(25)), // state
            r.getString(26) == null ? null : UUID.fromString(r.getString(26)), // subscriptionId
            r.getString(27) == null ? null : UUID.fromString(r.getString(27)) //bundleId
        );

        // Avoid creating a dummy subscriptions with all null fields
        if (next.getProductName() == null && next.getSlug() == null) {
            next = null;
        }

        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.valueOf(r.getString(3));

        return new BusinessSubscriptionTransition(
            r.getString(1),
            new DateTime(r.getLong(2), DateTimeZone.UTC),
            event,
            prev,
            next
        );
    }
}
