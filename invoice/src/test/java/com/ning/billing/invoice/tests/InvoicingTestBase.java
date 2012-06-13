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

package com.ning.billing.invoice.tests;

import javax.annotation.Nullable;
import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.model.InvoicingConfiguration;

public abstract class InvoicingTestBase {
    protected static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();
    protected static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMode();

    protected static final BigDecimal ZERO = new BigDecimal("0.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal ONE_HALF = new BigDecimal("0.5").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal ONE = new BigDecimal("1.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal ONE_AND_A_HALF = new BigDecimal("1.5").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWO = new BigDecimal("2.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THREE = new BigDecimal("3.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal FOUR = new BigDecimal("4.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal FIVE = new BigDecimal("5.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal SIX = new BigDecimal("6.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal SEVEN = new BigDecimal("7.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal EIGHT = new BigDecimal("8.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal TEN = new BigDecimal("10.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal ELEVEN = new BigDecimal("11.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWELVE = new BigDecimal("12.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTEEN = new BigDecimal("13.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal FOURTEEN = new BigDecimal("14.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal FIFTEEN = new BigDecimal("15.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal NINETEEN = new BigDecimal("19.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWENTY = new BigDecimal("20.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal TWENTY_FOUR = new BigDecimal("24.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWENTY_FIVE = new BigDecimal("25.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal TWENTY_SEVEN = new BigDecimal("27.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWENTY_EIGHT = new BigDecimal("28.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWENTY_NINE = new BigDecimal("29.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTY = new BigDecimal("30.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTY_ONE = new BigDecimal("31.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal FORTY = new BigDecimal("40.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal EIGHTY_NINE = new BigDecimal("89.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal NINETY = new BigDecimal("90.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal NINETY_ONE = new BigDecimal("91.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal NINETY_TWO = new BigDecimal("92.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal THREE_HUNDRED_AND_SIXTY_FIVE = new BigDecimal("365.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THREE_HUNDRED_AND_SIXTY_SIX = new BigDecimal("366.0").setScale(NUMBER_OF_DECIMALS);

    protected DateTime buildDateTime(final int year, final int month, final int day) {
        return new DateTime(year, month, day, 0, 0, 0, 0);
    }

    protected BillingEvent createMockBillingEvent(@Nullable final Account account, final Subscription subscription,
                                                  final DateTime effectiveDate,
                                                  final Plan plan, final PlanPhase planPhase,
                                                  @Nullable final BigDecimal fixedPrice, @Nullable final BigDecimal recurringPrice,
                                                  final Currency currency, final BillingPeriod billingPeriod, final int billCycleDay,
                                                  final BillingModeType billingModeType, final String description,
                                                  final long totalOrdering,
                                                  final SubscriptionTransitionType type) {
        return new BillingEvent() {
            @Override
            public Account getAccount() {
                return account;
            }

            @Override
            public int getBillCycleDay() {
                return billCycleDay;
            }

            @Override
            public Subscription getSubscription() {
                return subscription;
            }

            @Override
            public DateTime getEffectiveDate() {
                return effectiveDate;
            }

            @Override
            public PlanPhase getPlanPhase() {
                return planPhase;
            }

            @Override
            public Plan getPlan() {
                return plan;
            }

            @Override
            public BillingPeriod getBillingPeriod() {
                return billingPeriod;
            }

            @Override
            public BillingModeType getBillingMode() {
                return billingModeType;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public BigDecimal getFixedPrice() {
                return fixedPrice;
            }

            @Override
            public BigDecimal getRecurringPrice() {
                return recurringPrice;
            }

            @Override
            public Currency getCurrency() {
                return currency;
            }

            @Override
            public SubscriptionTransitionType getTransitionType() {
                return type;
            }

            @Override
            public Long getTotalOrdering() {
                return totalOrdering;
            }

            @Override
            public DateTimeZone getTimeZone() {
                return DateTimeZone.UTC;
            }

            @Override
            public int compareTo(final BillingEvent e1) {
                if (!getSubscription().getId().equals(e1.getSubscription().getId())) { // First order by subscription
                    return getSubscription().getId().compareTo(e1.getSubscription().getId());
                } else { // subscriptions are the same
                    if (!getEffectiveDate().equals(e1.getEffectiveDate())) { // Secondly order by date
                        return getEffectiveDate().compareTo(e1.getEffectiveDate());
                    } else { // dates and subscriptions are the same
                        return getTotalOrdering().compareTo(e1.getTotalOrdering());
                    }
                }
            }
        };
    }
}
