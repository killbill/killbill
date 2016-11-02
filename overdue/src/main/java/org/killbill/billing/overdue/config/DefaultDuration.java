/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.overdue.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;

import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultDuration extends ValidatingConfig<DefaultOverdueConfig> implements Duration {
    @XmlElement(required = true)
    private TimeUnit unit;

    @XmlElement(required = false)
    private Integer number = -1;

    @Override
    public TimeUnit getUnit() {
        return unit;
    }

    @Override
    public int getNumber() {
        return number;
    }

    @Override
    public DateTime addToDateTime(final DateTime dateTime) {
        if ((number == null) && (unit != TimeUnit.UNLIMITED)) {
            return dateTime;
        }

        switch (unit) {
            case DAYS:
                return dateTime.plusDays(number);
            case MONTHS:
                return dateTime.plusMonths(number);
            case YEARS:
                return dateTime.plusYears(number);
            case UNLIMITED:
            default:
                throw new  IllegalStateException("Unexpected duration unit " + unit);
        }
    }

    @Override
    public LocalDate addToLocalDate(final LocalDate localDate) {
        if ((number == null) && (unit != TimeUnit.UNLIMITED)) {
            return localDate;
        }

        switch (unit) {
            case DAYS:
                return localDate.plusDays(number);
            case MONTHS:
                return localDate.plusMonths(number);
            case YEARS:
                return localDate.plusYears(number);
            case UNLIMITED:
            default:
                throw new  IllegalStateException("Unexpected duration unit " + unit);
        }
    }
    @Override
    public Period toJodaPeriod() {
        if ((number == null) && (unit != TimeUnit.UNLIMITED)) {
            return new Period();
        }

        switch (unit) {
            case DAYS:
                return new Period().withDays(number);
            case MONTHS:
                return new Period().withMonths(number);
            case YEARS:
                return new Period().withYears(number);
            case UNLIMITED:
            default:
                throw new  IllegalStateException("Unexpected duration unit " + unit);
        }
    }

    @Override
    public ValidationErrors validate(final DefaultOverdueConfig catalog, final ValidationErrors errors) {
        //TODO MDW - Validation TimeUnit UNLIMITED iff number == -1
        return errors;
    }

    public DefaultDuration setUnit(final TimeUnit unit) {
        this.unit = unit;
        return this;
    }

    public DefaultDuration setNumber(final Integer number) {
        this.number = number;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultDuration{");
        sb.append("unit=").append(unit);
        sb.append(", number=").append(number);
        sb.append('}');
        return sb.toString();
    }
}
