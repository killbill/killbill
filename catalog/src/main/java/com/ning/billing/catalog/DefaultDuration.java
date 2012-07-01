/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.catalog;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.joda.time.DateTime;
import org.joda.time.Period;

import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultDuration extends ValidatingConfig<StandaloneCatalog> implements Duration {
    @XmlElement(required = true)
    private TimeUnit unit;

    @XmlElement(required = false)
    private Integer number = -1;

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IDuration#getUnit()
      */
    @Override
    public TimeUnit getUnit() {
        return unit;
    }

    /* (non-Javadoc)
	 * @see com.ning.billing.catalog.IDuration#getLength()
	 */
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
                return dateTime.plusYears(100);
            default:
                return dateTime;
        }
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        //Validation: TimeUnit UNLIMITED iff number == -1
        if ((unit == TimeUnit.UNLIMITED && number != -1)) {
            errors.add(new ValidationError("Duration can only have 'UNLIMITED' unit if the number is omitted.",
                                           catalog.getCatalogURI(), DefaultPlanPhase.class, ""));
        }

        //TODO MDW - Validation TimeUnit UNLIMITED iff number == -1
        return errors;
    }

    protected DefaultDuration setUnit(final TimeUnit unit) {
        this.unit = unit;
        return this;
    }

    protected DefaultDuration setNumber(final Integer number) {
        this.number = number;
        return this;
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
                return new Period().withYears(100);
            default:
                return new Period();
        }
    }
}
