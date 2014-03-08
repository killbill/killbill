/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util;

import java.math.BigDecimal;

public class DefaultAmountFormatter {

    public static final int SCALE = 2;

    // Static only
    private DefaultAmountFormatter() {
    }

    public static BigDecimal round(final BigDecimal decimal) {
        if (decimal == null) {
            return BigDecimal.ZERO.setScale(SCALE, BigDecimal.ROUND_HALF_UP);
        } else {
            return decimal.setScale(SCALE, BigDecimal.ROUND_HALF_UP);
        }
    }
}
