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
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;

public class BusinessSubscriptionTransitionMapper implements ResultSetMapper<BusinessSubscriptionTransition>
{
    @Override
    public BusinessSubscriptionTransition map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException
    {
        BusinessSubscription prev = new BusinessSubscription(
            r.getString(5), // productName
            r.getString(6), // productType
            r.getString(7) == null ? null : ProductCategory.valueOf(r.getString(7)), // productCategory
            r.getString(8), // slug
            r.getString(9),  // phase
            r.getString(10),  // billing period
            BigDecimal.valueOf(r.getDouble(11)), // price
            r.getString(12), // priceList
            BigDecimal.valueOf(r.getDouble(13)), // mrr
            r.getString(14), // currency
            r.getLong(15) == 0 ? null : new DateTime(r.getLong(15), DateTimeZone.UTC), // startDate
            r.getString(16) == null ? null : SubscriptionState.valueOf(r.getString(16)), // state
            r.getString(17) == null ? null : UUID.fromString(r.getString(17)), // subscriptionId
            r.getString(18) == null ? null : UUID.fromString(r.getString(18)) //bundleId
        );

        // Avoid creating a dummy subscriptions with all null fields
        if (prev.getProductName() == null && prev.getSlug() == null) {
            prev = null;
        }

        BusinessSubscription next = new BusinessSubscription(
            r.getString(19), // productName
            r.getString(20), // productType
            r.getString(21) == null ? null : ProductCategory.valueOf(r.getString(21)), // productCategory
            r.getString(22), // slug8
            r.getString(23),  // phase
            r.getString(24),  // billing period
            BigDecimal.valueOf(r.getDouble(25)), // price
            r.getString(26), // priceList
            BigDecimal.valueOf(r.getDouble(27)), // mrr
            r.getString(28), // currency
            r.getLong(29) == 0 ? null : new DateTime(r.getLong(29), DateTimeZone.UTC), // startDate
            r.getString(30) == null ? null : SubscriptionState.valueOf(r.getString(30)), // state
            r.getString(31) == null ? null : UUID.fromString(r.getString(31)), // subscriptionId
            r.getString(32) == null ? null : UUID.fromString(r.getString(32)) //bundleId
        );

        // Avoid creating a dummy subscriptions with all null fields
        if (next.getProductName() == null && next.getSlug() == null) {
            next = null;
        }

        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.valueOf(r.getString(4));

        return new BusinessSubscriptionTransition(
            r.getString(1),
            r.getString(2),
            new DateTime(r.getLong(3), DateTimeZone.UTC),
            event,
            prev,
            next
        );
    }
}
