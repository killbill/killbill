/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.usage.api;

import java.util.Objects;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.usage.plugin.api.UsageContext;
import org.killbill.billing.util.callcontext.TenantContext;

public class DefaultUsageContext implements UsageContext {

    private final DryRunType dryRunType;
    private final LocalDate targetDate;
    private final TenantContext context;

    public DefaultUsageContext(final DryRunType dryRunType, final LocalDate targetDate, final TenantContext context) {
        this.dryRunType = dryRunType;
        this.targetDate = targetDate;
        this.context = context;
    }

    @Override
    public DryRunType getDryRunType() {
        return dryRunType;
    }

    @Override
    public LocalDate getInputTargetDate() {
        return targetDate;
    }

    @Override
    public UUID getAccountId() {
        return context.getAccountId();
    }

    @Override
    public UUID getTenantId() {
        return context.getTenantId();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultUsageContext that = (DefaultUsageContext) o;

        if (dryRunType != that.dryRunType) {
            return false;
        }
        if (targetDate != null ? targetDate.compareTo(that.targetDate) != 0 : that.targetDate != null) {
            return false;
        }
        return context != null ? context.equals(that.context) : that.context == null;
    }

    @Override
    public int hashCode() {
        int result = dryRunType != null ? dryRunType.hashCode() : 0;
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (context != null ? context.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DefaultUsageContext{" +
               "dryRunType=" + dryRunType +
               ", targetDate=" + targetDate +
               ", context=" + context +
               '}';
    }
}
