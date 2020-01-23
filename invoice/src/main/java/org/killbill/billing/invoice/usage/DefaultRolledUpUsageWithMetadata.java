/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.invoice.usage;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.usage.api.RolledUpUnit;

public class DefaultRolledUpUsageWithMetadata implements RolledUpUsageWithMetadata {

    private final UUID subscriptionId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final List<RolledUpUnit> rolledUpUnits;
    private final DateTime catalogEffectiveDate;

    public DefaultRolledUpUsageWithMetadata(final UUID subscriptionId, final LocalDate startDate, final LocalDate endDate, final List<RolledUpUnit> rolledUpUnits, final DateTime catalogEffectiveDate) {
        this.subscriptionId = subscriptionId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.rolledUpUnits = rolledUpUnits;
        this.catalogEffectiveDate = catalogEffectiveDate;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public LocalDate getStart() {
        return startDate;
    }

    @Override
    public LocalDate getEnd() {
        return endDate;
    }

    @Override
    public List<RolledUpUnit> getRolledUpUnits() {
        return rolledUpUnits;
    }

    @Override
    public DateTime getCatalogEffectiveDate() {
        return catalogEffectiveDate;
    }
}

