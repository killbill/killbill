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


import com.ning.billing.invoice.model.InvoicingConfiguration;
import org.joda.time.DateTime;

import java.math.BigDecimal;

public abstract class InvoicingTestBase {
    protected static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();
    protected static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMethod();

    protected static final BigDecimal ZERO = new BigDecimal("0.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal ONE_HALF = new BigDecimal("0.5").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal ONE = new BigDecimal("1.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal ONE_AND_A_HALF = new BigDecimal("1.5").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWO = new BigDecimal("2.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THREE = new BigDecimal("3.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal FOUR = new BigDecimal("4.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal FIVE = new BigDecimal("5.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal SEVEN = new BigDecimal("7.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal EIGHT = new BigDecimal("8.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal TEN = new BigDecimal("10.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal TWELVE = new BigDecimal("12.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTEEN = new BigDecimal("13.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal FOURTEEN = new BigDecimal("14.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal FIFTEEN = new BigDecimal("15.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal TWENTY = new BigDecimal("20.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWENTY_ONE = new BigDecimal("21.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal TWENTY_FOUR = new BigDecimal("24.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal TWENTY_SIX = new BigDecimal("26.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal TWENTY_EIGHT = new BigDecimal("28.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWENTY_NINE = new BigDecimal("29.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTY = new BigDecimal("30.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTY_ONE = new BigDecimal("31.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal EIGHTY_NINE = new BigDecimal("89.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal NINETY = new BigDecimal("90.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal NINETY_ONE = new BigDecimal("91.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal NINETY_TWO = new BigDecimal("92.0").setScale(NUMBER_OF_DECIMALS);

    protected static final BigDecimal THREE_HUNDRED_AND_SIXTY_FIVE = new BigDecimal("365.0").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THREE_HUNDRED_AND_SIXTY_SIX = new BigDecimal("366.0").setScale(NUMBER_OF_DECIMALS);

    protected DateTime buildDateTime(int year, int month, int day) {
        return new DateTime(year, month, day, 0, 0, 0, 0);
    }
}
