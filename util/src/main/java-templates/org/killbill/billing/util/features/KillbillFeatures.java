/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.features;

import com.google.common.base.MoreObjects;

public class KillbillFeatures {

    public static final String PROP_FEATURE_INVOICE_OPTIMIZATION = "killbill.features.invoice.optimization";
    public static final String PROP_FEATURE_BUS_OPTIMIZATION = "killbill.features.bus.optimization";
    public static final String PROP_FEATURE_ALLOW_ACCOUNT_BCD_UPDATE = "killbill.features.account.allowBCDUpdate";

    private static final String FEATURE_INVOICE_OPTIMIZATION = "${killbill.features.invoice.optimization}";
    private static final String FEATURE_BUS_OPTIMIZATION = "${killbill.features.bus.optimization}";
    private static final String FEATURE_ALLOW_ACCOUNT_BCD_UPDATE = "${killbill.features.account.allowBCDUpdate}";

    private final boolean isInvoiceOptimizationOn;
    private final boolean isBusOptimizationOn;
    private final boolean allowAccountBCDUpdate;

    public KillbillFeatures() {
        this.isInvoiceOptimizationOn = Boolean.valueOf(MoreObjects.<String>firstNonNull(FEATURE_INVOICE_OPTIMIZATION, "false"));
        this.isBusOptimizationOn = Boolean.valueOf(MoreObjects.<String>firstNonNull(FEATURE_BUS_OPTIMIZATION, "false"));
        this.allowAccountBCDUpdate = Boolean.valueOf(MoreObjects.<String>firstNonNull(FEATURE_ALLOW_ACCOUNT_BCD_UPDATE, "false"));
    }

    public boolean isInvoiceOptimizationOn() {
        return isInvoiceOptimizationOn;
    }

    public boolean isBusOptimizationOn() {
        return isBusOptimizationOn;
    }

    public boolean allowAccountBCDUpdate() {
        return allowAccountBCDUpdate;
    }
}
