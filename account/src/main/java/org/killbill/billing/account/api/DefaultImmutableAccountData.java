/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.util.account.AccountDateTimeUtils;

public class DefaultImmutableAccountData implements ImmutableAccountData {

    private final UUID id;
    private final String externalKey;
    private final Currency currency;
    private final DateTimeZone dateTimeZone;
    private final UUID parentAccountId;
    private final boolean isPaymentDelegatedToParent;
    private final DateTimeZone fixedOffsetDateTimeZone;
    private final DateTime referenceTime;

    public DefaultImmutableAccountData(final UUID id, final String externalKey, final Currency currency, final DateTimeZone dateTimeZone, final DateTimeZone fixedOffsetDateTimeZone, final DateTime referenceTime, final UUID parentAccountId, final boolean isPaymentDelegatedToParent) {
        this.id = id;
        this.externalKey = externalKey;
        this.currency = currency;
        this.dateTimeZone = dateTimeZone;
        this.fixedOffsetDateTimeZone = fixedOffsetDateTimeZone;
        this.referenceTime = referenceTime;
        this.parentAccountId = parentAccountId;
        this.isPaymentDelegatedToParent = isPaymentDelegatedToParent;
    }

    public DefaultImmutableAccountData(final Account account) {
        this(account.getId(), account.getExternalKey(), account.getCurrency(), account.getTimeZone(),
             AccountDateTimeUtils.getFixedOffsetTimeZone(account), AccountDateTimeUtils.getReferenceDateTime(account),
             account.getParentAccountId(), account.isPaymentDelegatedToParent());
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

    @Override
    public UUID getParentAccountId() {
        return parentAccountId;
    }

    @Override
    public Boolean isPaymentDelegatedToParent() {
        return isPaymentDelegatedToParent;
    }

    public DateTimeZone getFixedOffsetTimeZone() {
        return fixedOffsetDateTimeZone;
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
        sb.append(", dateTimeZone=").append(dateTimeZone);
        sb.append(", fixedOffsetDateTimeZone=").append(fixedOffsetDateTimeZone);
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
        if (dateTimeZone != null ? !dateTimeZone.equals(that.dateTimeZone) : that.dateTimeZone != null) {
            return false;
        }
        if (fixedOffsetDateTimeZone != null ? !fixedOffsetDateTimeZone.equals(that.fixedOffsetDateTimeZone) : that.fixedOffsetDateTimeZone != null) {
            return false;
        }
        return referenceTime != null ? referenceTime.compareTo(that.referenceTime) == 0 : that.referenceTime == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (dateTimeZone != null ? dateTimeZone.hashCode() : 0);
        result = 31 * result + (fixedOffsetDateTimeZone != null ? fixedOffsetDateTimeZone.hashCode() : 0);
        result = 31 * result + (referenceTime != null ? referenceTime.hashCode() : 0);
        return result;
    }
}
