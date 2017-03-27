/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.catalog;

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.catalog.api.Limit;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultLimit extends ValidatingConfig<StandaloneCatalog> implements Limit {

    @XmlElement(required = true)
    @XmlIDREF
    private DefaultUnit unit;

    @XmlElement(required = false)
    private Double max;

    @XmlElement(required = false)
    private Double min;

    /* (non-Javadoc)
     * @see org.killbill.billing.catalog.Limit#getUnit()
     */
    @Override
    public DefaultUnit getUnit() {
        return unit;
    }

    /* (non-Javadoc)
     * @see org.killbill.billing.catalog.Limit#getMax()
     */
    @Override
    public Double getMax() {
        return max;
    }

    /* (non-Javadoc)
     * @see org.killbill.billing.catalog.Limit#getMin()
     */
    @Override
    public Double getMin() {
        return min;
    }

    @Override
    public ValidationErrors validate(StandaloneCatalog root, ValidationErrors errors) {
        if (!CatalogSafetyInitializer.DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE.equals(max) &&
            !CatalogSafetyInitializer.DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE.equals(min) &&
                   max.doubleValue() < min.doubleValue()) {
            errors.add(new ValidationError("max must be greater than min", root.getCatalogURI(), Limit.class, ""));
        }
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }


    @Override
    public boolean compliesWith(double value) {
        if (!CatalogSafetyInitializer.DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE.equals(max)) {
            if (value > max.doubleValue()) {
                return false;
            }
        }
        if (!CatalogSafetyInitializer.DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE.equals(min)) {
            if (value < min.doubleValue()) {
                return false;
            }
        }
        return true;
    }

    public DefaultLimit setUnit(final DefaultUnit unit) {
        this.unit = unit;
        return this;
    }

    public DefaultLimit setMax(final Double max) {
        this.max = max;
        return this;
    }

    public DefaultLimit setMin(final Double min) {
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
        if (unit != null ? !unit.equals(that.unit) : that.unit != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = unit != null ? unit.hashCode() : 0;
        result = 31 * result + max.hashCode();
        result = 31 * result + min.hashCode();
        return result;
    }
}
