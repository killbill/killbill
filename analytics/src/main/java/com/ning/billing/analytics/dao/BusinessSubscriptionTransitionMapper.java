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
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.catalog.api.ProductCategory;

import static com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;

public class BusinessSubscriptionTransitionMapper implements ResultSetMapper<BusinessSubscriptionTransitionModelDao> {

    @Override
    public BusinessSubscriptionTransitionModelDao map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        BusinessSubscription prev = new BusinessSubscription(
                r.getString(9), // productName
                r.getString(10), // productType
                r.getString(11) == null ? null : ProductCategory.valueOf(r.getString(11)), // productCategory
                r.getString(12), // slug
                r.getString(13),  // phase
                r.getString(14),  // billing period
                BigDecimal.valueOf(r.getDouble(15)), // price
                r.getString(16), // priceList
                BigDecimal.valueOf(r.getDouble(17)), // mrr
                r.getString(18), // currency
                r.getLong(19) == 0 ? null : new DateTime(r.getLong(19), DateTimeZone.UTC), // startDate
                r.getString(20) == null ? null : SubscriptionState.valueOf(r.getString(20)) // state
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
                r.getString(32) == null ? null : SubscriptionState.valueOf(r.getString(32)) // state
        );

        // Avoid creating a dummy subscriptions with all null fields
        if (next.getProductName() == null && next.getSlug() == null) {
            next = null;
        }

        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.valueOf(r.getString(8));

        return new BusinessSubscriptionTransitionModelDao(
                r.getLong(1),
                UUID.fromString(r.getString(2)),
                r.getString(3),
                UUID.fromString(r.getString(4)),
                r.getString(5),
                UUID.fromString(r.getString(6)),
                new DateTime(r.getLong(7), DateTimeZone.UTC),
                event,
                prev,
                next
        );
    }
}
