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

package org.killbill.billing.usage.api.user;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.usage.api.RolledUpUsage;

public class DefaultRolledUpUsage implements RolledUpUsage {

    private final UUID subscriptionId;
    private final DateTime startDate;
    private final DateTime endDate;
    private final List<RolledUpUnit> rolledUpUnits;

    public DefaultRolledUpUsage(final UUID subscriptionId, final DateTime startDate, final DateTime endDate, final List<RolledUpUnit> rolledUpUnits) {
        this.subscriptionId = subscriptionId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.rolledUpUnits = rolledUpUnits;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public DateTime getStart() {
        return startDate;
    }

    @Override
    public DateTime getEnd() {
        return endDate;
    }

    @Override
    public List<RolledUpUnit> getRolledUpUnits() {
        return rolledUpUnits;
    }
}
