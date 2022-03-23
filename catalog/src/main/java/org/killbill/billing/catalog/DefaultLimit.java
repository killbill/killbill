/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.catalog;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigDecimal;
import java.util.Objects;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlTransient;

import org.killbill.billing.catalog.api.Limit;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

import static org.killbill.billing.catalog.CatalogSafetyInitializer.DEFAULT_NON_REQUIRED_BIGDECIMAL_FIELD_VALUE;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultLimit extends ValidatingConfig<StandaloneCatalog> implements Limit, Externalizable {

    @XmlElement(required = true)
    @XmlIDREF
    private DefaultUnit unit;

    @XmlElement(required = false)
    private BigDecimal max;

    @XmlElement(required = false)
    private BigDecimal min;

    /**
     * True if {@link #max} is not null and not contains default value (-1) defined in
     * {@link CatalogSafetyInitializer#DEFAULT_NON_REQUIRED_BIGDECIMAL_FIELD_VALUE}
     */
    @XmlTransient
    private boolean maxHasValue;

    /**
     * True if {@link #min} is not null and not contains default value (-1) defined in
     * {@link CatalogSafetyInitializer#DEFAULT_NON_REQUIRED_BIGDECIMAL_FIELD_VALUE}
     */
    @XmlTransient
    private boolean minHasValue;

    @Override
    public DefaultUnit getUnit() {
        return unit;
    }

    @Override
    public BigDecimal getMax() {
        return max;
    }

    @Override
    public BigDecimal getMin() {
        return min;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog root, final ValidationErrors errors) {
        if (maxHasValue && minHasValue && max.compareTo(min) < 0) {
            errors.add(new ValidationError("max must be greater than min", Limit.class, ""));
        }
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
        maxHasValue = max != null && max.compareTo(DEFAULT_NON_REQUIRED_BIGDECIMAL_FIELD_VALUE) != 0;
        minHasValue = min != null && min.compareTo(DEFAULT_NON_REQUIRED_BIGDECIMAL_FIELD_VALUE) != 0;
    }

    @Override
    public boolean compliesWith(final BigDecimal value) {
        if (maxHasValue && value.compareTo(max) > 0) {
            return false;
        }
        return !minHasValue || value.compareTo(min) <= 0;
    }

    public DefaultLimit setUnit(final DefaultUnit unit) {
        this.unit = unit;
        return this;
    }

    public DefaultLimit setMax(final BigDecimal max) {
        this.max = max;
        return this;
    }

    public DefaultLimit setMin(final BigDecimal min) {
        this.min = min;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultLimit)) {
            return false;
        }

        final DefaultLimit that = (DefaultLimit) o;

        if (!max.equals(that.max)) {
            return false;
        }
        if (!min.equals(that.min)) {
            return false;
        }
        return Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        int result = unit != null ? unit.hashCode() : 0;
        result = 31 * result + (max != null ? max.hashCode() : 0);
        result = 31 * result + (min != null ? min.hashCode() : 0);
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeObject(unit);
        out.writeObject(max);
        out.writeObject(min);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.unit = (DefaultUnit) in.readObject();
        this.max = (BigDecimal) in.readObject();
        this.min = (BigDecimal) in.readObject();
    }
}
