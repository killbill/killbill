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

package com.ning.billing.osgi.bundles.analytics.dao;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.Types;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.osgi.bundles.analytics.model.BusinessSubscription;
import com.ning.billing.osgi.bundles.analytics.model.BusinessSubscriptionTransitionModelDao;

@BindingAnnotation(BusinessSubscriptionTransitionBinder.BstBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface BusinessSubscriptionTransitionBinder {

    public static class BstBinderFactory implements BinderFactory {

        public Binder build(final Annotation annotation) {
            return new Binder<BusinessSubscriptionTransitionBinder, BusinessSubscriptionTransitionModelDao>() {
                public void bind(final SQLStatement q, final BusinessSubscriptionTransitionBinder bind, final BusinessSubscriptionTransitionModelDao arg) {
                    q.bind("total_ordering", arg.getTotalOrdering());
                    q.bind("bundle_id", arg.getBundleId().toString());
                    q.bind("external_key", arg.getExternalKey());
                    q.bind("account_id", arg.getAccountId().toString());
                    q.bind("account_key", arg.getAccountKey());
                    q.bind("subscription_id", arg.getSubscriptionId().toString());
                    q.bind("requested_timestamp", arg.getRequestedTimestamp().getMillis());
                    q.bind("event", arg.getEvent().toString());

                    final BusinessSubscription previousSubscription = arg.getPreviousSubscription();
                    if (previousSubscription == null) {
                        q.bindNull("prev_product_name", Types.VARCHAR);
                        q.bindNull("prev_product_type", Types.VARCHAR);
                        q.bindNull("prev_product_category", Types.VARCHAR);
                        q.bindNull("prev_slug", Types.VARCHAR);
                        q.bindNull("prev_phase", Types.VARCHAR);
                        q.bindNull("prev_billing_period", Types.VARCHAR);
                        q.bindNull("prev_price", Types.NUMERIC);
                        q.bindNull("prev_price_list", Types.VARCHAR);
                        q.bindNull("prev_mrr", Types.NUMERIC);
                        q.bindNull("prev_currency", Types.VARCHAR);
                        q.bindNull("prev_start_date", Types.BIGINT);
                        q.bindNull("prev_state", Types.VARCHAR);
                    } else {
                        q.bind("prev_product_name", previousSubscription.getProductName());
                        q.bind("prev_product_type", previousSubscription.getProductType());
                        if (previousSubscription.getProductCategory() == null) {
                            q.bindNull("prev_product_category", Types.VARCHAR);
                        } else {
                            q.bind("prev_product_category", previousSubscription.getProductCategory().toString());
                        }
                        q.bind("prev_slug", previousSubscription.getSlug());
                        q.bind("prev_phase", previousSubscription.getPhase());
                        q.bind("prev_billing_period", previousSubscription.getBillingPeriod());
                        q.bind("prev_price", previousSubscription.getRoundedPrice());
                        q.bind("prev_price_list", previousSubscription.getPriceList());
                        q.bind("prev_mrr", previousSubscription.getRoundedMrr());
                        q.bind("prev_currency", previousSubscription.getCurrency());
                        if (previousSubscription.getStartDate() == null) {
                            q.bindNull("prev_start_date", Types.BIGINT);
                        } else {
                            q.bind("prev_start_date", previousSubscription.getStartDate().getMillis());
                        }
                        if (previousSubscription.getState() == null) {
                            q.bindNull("prev_state", Types.VARCHAR);
                        } else {
                            q.bind("prev_state", previousSubscription.getState().toString());
                        }
                    }

                    final BusinessSubscription nextSubscription = arg.getNextSubscription();
                    if (nextSubscription == null) {
                        q.bindNull("next_product_name", Types.VARCHAR);
                        q.bindNull("next_product_type", Types.VARCHAR);
                        q.bindNull("next_product_category", Types.VARCHAR);
                        q.bindNull("next_slug", Types.VARCHAR);
                        q.bindNull("next_phase", Types.VARCHAR);
                        q.bindNull("next_billing_period", Types.VARCHAR);
                        q.bindNull("next_price", Types.NUMERIC);
                        q.bindNull("next_price_list", Types.VARCHAR);
                        q.bindNull("next_mrr", Types.NUMERIC);
                        q.bindNull("next_currency", Types.VARCHAR);
                        q.bindNull("next_start_date", Types.BIGINT);
                        q.bindNull("next_state", Types.VARCHAR);
                    } else {
                        q.bind("next_product_name", nextSubscription.getProductName());
                        q.bind("next_product_type", nextSubscription.getProductType());
                        if (nextSubscription.getProductCategory() == null) {
                            q.bindNull("next_product_category", Types.VARCHAR);
                        } else {
                            q.bind("next_product_category", nextSubscription.getProductCategory().toString());
                        }
                        q.bind("next_slug", nextSubscription.getSlug());
                        q.bind("next_phase", nextSubscription.getPhase());
                        q.bind("next_billing_period", nextSubscription.getBillingPeriod());
                        q.bind("next_price", nextSubscription.getRoundedPrice());
                        q.bind("next_price_list", nextSubscription.getPriceList());
                        q.bind("next_mrr", nextSubscription.getRoundedMrr());
                        q.bind("next_currency", nextSubscription.getCurrency());
                        if (nextSubscription.getStartDate() == null) {
                            q.bindNull("next_start_date", Types.BIGINT);
                        } else {
                            q.bind("next_start_date", nextSubscription.getStartDate().getMillis());
                        }
                        if (nextSubscription.getState() == null) {
                            q.bindNull("next_state", Types.VARCHAR);
                        } else {
                            q.bind("next_state", nextSubscription.getState().toString());
                        }
                    }
                }
            };
        }
    }
}
