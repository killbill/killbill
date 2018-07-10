/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.util.account.AccountDateTimeUtils;
import org.killbill.billing.util.cache.ExternalizableInput;
import org.killbill.billing.util.cache.ExternalizableOutput;
import org.killbill.billing.util.cache.MapperHolder;

public class DefaultImmutableAccountData implements ImmutableAccountData, Externalizable {

    private static final long serialVersionUID = 8117686452347277415L;

    private UUID id;
    private String externalKey;
    private Currency currency;
    private DateTimeZone timeZone;
    private DateTimeZone fixedOffsetTimeZone;
    private DateTime referenceTime;

    // For deserialization
    public DefaultImmutableAccountData() {}

    public DefaultImmutableAccountData(final UUID id, final String externalKey, final Currency currency, final DateTimeZone timeZone, final DateTimeZone fixedOffsetTimeZone, final DateTime referenceTime) {
        this.id = id;
        this.externalKey = externalKey;
        this.currency = currency;
        this.timeZone = timeZone;
        this.fixedOffsetTimeZone = fixedOffsetTimeZone;
        this.referenceTime = referenceTime;
    }

    public DefaultImmutableAccountData(final Account account) {
        this(account.getId(),
             account.getExternalKey(),
             account.getCurrency(),
             account.getTimeZone(),
             AccountDateTimeUtils.getFixedOffsetTimeZone(account),
             account.getReferenceTime());
    }

    public DefaultImmutableAccountData(final AccountModelDao account) {
        this(account.getId(),
             account.getExternalKey(),
             account.getCurrency(),
             account.getTimeZone(),
             AccountDateTimeUtils.getFixedOffsetTimeZone(account),
             account.getReferenceTime());
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
        return timeZone;
    }

    public DateTimeZone getFixedOffsetTimeZone() {
        return fixedOffsetTimeZone;
    }

    @Override
    public DateTime getReferenceTime() {
        return referenceTime;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultImmutableAccountData{");
        sb.append("id=").append(id);
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", currency=").append(currency);
        sb.append(", timeZone=").append(timeZone);
        sb.append(", fixedOffsetTimeZone=").append(fixedOffsetTimeZone);
        sb.append(", referenceTime=").append(referenceTime);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultImmutableAccountData that = (DefaultImmutableAccountData) o;

        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (timeZone != null ? !timeZone.equals(that.timeZone) : that.timeZone != null) {
            return false;
        }
        if (fixedOffsetTimeZone != null ? !fixedOffsetTimeZone.equals(that.fixedOffsetTimeZone) : that.fixedOffsetTimeZone != null) {
            return false;
        }
        return referenceTime != null ? referenceTime.compareTo(that.referenceTime) == 0 : that.referenceTime == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (timeZone != null ? timeZone.hashCode() : 0);
        result = 31 * result + (fixedOffsetTimeZone != null ? fixedOffsetTimeZone.hashCode() : 0);
        result = 31 * result + (referenceTime != null ? referenceTime.hashCode() : 0);
        return result;
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        MapperHolder.mapper().readerForUpdating(this).readValue(new ExternalizableInput(in));
    }

    @Override
    public void writeExternal(final ObjectOutput oo) throws IOException {
        MapperHolder.mapper().writeValue(new ExternalizableOutput(oo), this);
    }
}
