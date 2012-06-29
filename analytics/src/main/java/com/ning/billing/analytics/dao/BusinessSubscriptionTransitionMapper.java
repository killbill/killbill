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
                r.getString(7), // productName
                r.getString(8), // productType
                r.getString(9) == null ? null : ProductCategory.valueOf(r.getString(9)), // productCategory
                r.getString(10), // slug
                r.getString(11),  // phase
                r.getString(12),  // billing period
                BigDecimal.valueOf(r.getDouble(13)), // price
                r.getString(14), // priceList
                BigDecimal.valueOf(r.getDouble(15)), // mrr
                r.getString(16), // currency
                r.getLong(17) == 0 ? null : new DateTime(r.getLong(17), DateTimeZone.UTC), // startDate
                r.getString(18) == null ? null : SubscriptionState.valueOf(r.getString(18)), // state
                r.getString(19) == null ? null : UUID.fromString(r.getString(19)), // subscriptionId
                r.getString(20) == null ? null : UUID.fromString(r.getString(20)) //bundleId
        );

        // Avoid creating a dummy subscriptions with all null fields
        if (prev.getProductName() == null && prev.getSlug() == null) {
            prev = null;
        }

        BusinessSubscription next = new BusinessSubscription(
                r.getString(21), // productName
                r.getString(22), // productType
                r.getString(23) == null ? null : ProductCategory.valueOf(r.getString(23)), // productCategory
                r.getString(24), // slug8
                r.getString(25),  // phase
                r.getString(26),  // billing period
                BigDecimal.valueOf(r.getDouble(27)), // price
                r.getString(28), // priceList
                BigDecimal.valueOf(r.getDouble(29)), // mrr
                r.getString(30), // currency
                r.getLong(31) == 0 ? null : new DateTime(r.getLong(31), DateTimeZone.UTC), // startDate
                r.getString(32) == null ? null : SubscriptionState.valueOf(r.getString(32)), // state
                r.getString(33) == null ? null : UUID.fromString(r.getString(33)), // subscriptionId
                r.getString(34) == null ? null : UUID.fromString(r.getString(34)) //bundleId
        );

        // Avoid creating a dummy subscriptions with all null fields
        if (next.getProductName() == null && next.getSlug() == null) {
            next = null;
        }

        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.valueOf(r.getString(6));

        return new BusinessSubscriptionTransition(
                UUID.fromString(r.getString(1)),
                r.getLong(2),
                r.getString(3),
                r.getString(4),
                new DateTime(r.getLong(5), DateTimeZone.UTC),
                event,
                prev,
                next
        );
    }
}
