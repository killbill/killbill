/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.retry;

import org.joda.time.DateTime;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.payment.api.PluginProperty;

public class DefaultFailureCallResult implements OnFailurePaymentControlResult {

    private final Iterable<PluginProperty> adjustedPluginProperties;
    private final DateTime nextRetryDate;

    public DefaultFailureCallResult() {
        this(null, null);
    }

    public DefaultFailureCallResult(final DateTime nextRetryDate) {
        this(nextRetryDate, null);
    }

    public DefaultFailureCallResult(final DateTime nextRetryDate, final Iterable<PluginProperty> adjustedPluginProperties) {
        this.nextRetryDate = nextRetryDate;
        this.adjustedPluginProperties = adjustedPluginProperties;
    }

    @Override
    public DateTime getNextRetryDate() {
        return nextRetryDate;
    }

    @Override
    public Iterable<PluginProperty> getAdjustedPluginProperties() {
        return adjustedPluginProperties;
    }
}
