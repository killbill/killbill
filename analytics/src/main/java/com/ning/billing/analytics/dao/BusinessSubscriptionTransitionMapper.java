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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.analytics.model.BusinessSubscription;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;
import com.ning.billing.catalog.api.ProductCategory;

import static com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;

public class BusinessSubscriptionTransitionMapper implements ResultSetMapper<BusinessSubscriptionTransition> {
    @Override
    public BusinessSubscriptionTransition map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        BusinessSubscription prev = new BusinessSubscription(
                r.getString(6), // productName
                r.getString(7), // productType
                r.getString(8) == null ? null : ProductCategory.valueOf(r.getString(8)), // productCategory
                r.getString(9), // slug
                r.getString(10),  // phase
                r.getString(11),  // billing period
                BigDecimal.valueOf(r.getDouble(12)), // price
                r.getString(13), // priceList
                BigDecimal.valueOf(r.getDouble(14)), // mrr
                r.getString(15), // currency
                r.getLong(16) == 0 ? null : new DateTime(r.getLong(16), DateTimeZone.UTC), // startDate
                r.getString(17) == null ? null : SubscriptionState.valueOf(r.getString(17)), // state
                r.getString(18) == null ? null : UUID.fromString(r.getString(18)), // subscriptionId
                r.getString(19) == null ? null : UUID.fromString(r.getString(19)) //bundleId
        );

        // Avoid creating a dummy subscriptions with all null fields
        if (prev.getProductName() == null && prev.getSlug() == null) {
            prev = null;
        }

        BusinessSubscription next = new BusinessSubscription(
                r.getString(20), // productName
                r.getString(21), // productType
                r.getString(22) == null ? null : ProductCategory.valueOf(r.getString(22)), // productCategory
                r.getString(23), // slug8
                r.getString(24),  // phase
                r.getString(25),  // billing period
                BigDecimal.valueOf(r.getDouble(26)), // price
                r.getString(27), // priceList
                BigDecimal.valueOf(r.getDouble(28)), // mrr
                r.getString(29), // currency
                r.getLong(30) == 0 ? null : new DateTime(r.getLong(30), DateTimeZone.UTC), // startDate
                r.getString(31) == null ? null : SubscriptionState.valueOf(r.getString(31)), // state
                r.getString(32) == null ? null : UUID.fromString(r.getString(32)), // subscriptionId
                r.getString(33) == null ? null : UUID.fromString(r.getString(33)) //bundleId
        );

        // Avoid creating a dummy subscriptions with all null fields
        if (next.getProductName() == null && next.getSlug() == null) {
            next = null;
        }

        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.valueOf(r.getString(5));

        return new BusinessSubscriptionTransition(
                r.getLong(1),
                r.getString(2),
                r.getString(3),
                new DateTime(r.getLong(4), DateTimeZone.UTC),
                event,
                prev,
                next
        );
    }
}
