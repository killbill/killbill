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

package org.killbill.billing.account.api;

import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.Currency;

public class DefaultImmutableAccountData implements ImmutableAccountData {

    private final UUID id;
    private final String externalKey;
    private final Currency currency;
    private final DateTimeZone dateTimeZone;

    public DefaultImmutableAccountData(final UUID id, final String externalKey, final Currency currency, final DateTimeZone dateTimeZone) {
        this.id = id;
        this.externalKey = externalKey;
        this.currency = currency;
        this.dateTimeZone = dateTimeZone;
    }

    public DefaultImmutableAccountData(final Account account) {
        this(account.getId(), account.getExternalKey(), account.getCurrency(), account.getTimeZone());
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public DateTimeZone getTimeZone() {
        return dateTimeZone;
    }
}
