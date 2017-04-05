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

package org.killbill.billing.catalog;

import java.math.BigDecimal;
import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.Block;
import org.killbill.billing.catalog.api.BlockType;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultBlock extends ValidatingConfig<StandaloneCatalog> implements Block {

    @XmlAttribute(required = false)
    private BlockType type = BlockType.VANILLA;

    @XmlElement(required = true)
    @XmlIDREF
    private DefaultUnit unit;

    @XmlElement(required = true)
    private Double size;

    @XmlElement(required = true)
    private DefaultInternationalPrice prices;

    @XmlElement(required = false)
    private Double minTopUpCredit;

    // Not defined in catalog
    private PlanPhase phase;

    @Override
    public BlockType getType() {
        return type;
    }

    @Override
    public Unit getUnit() {
        return unit;
    }

    @Override
    public Double getSize() {
        return size;
    }

    @Override
    public InternationalPrice getPrice() {
        return prices;
    }

    @Override
    public Double getMinTopUpCredit() throws CatalogApiException {
        if (!CatalogSafetyInitializer.DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE.equals(minTopUpCredit) && type != BlockType.TOP_UP) {
            throw new CatalogApiException(ErrorCode.CAT_NOT_TOP_UP_BLOCK, phase.getName());
        }
        return minTopUpCredit;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        // Safety check
        if (type == null) {
            throw new IllegalStateException("type should have been automatically been initialized with VANILLA ");
        }

        if (type == BlockType.TOP_UP && CatalogSafetyInitializer.DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE.equals(minTopUpCredit)) {
            errors.add(new ValidationError(String.format("TOP_UP block needs to define minTopUpCredit for phase %s",
                                                         phase.getName()), catalog.getCatalogURI(), DefaultUsage.class, ""));
        }
        return errors;
    }

    public DefaultBlock() {
    }

    public DefaultBlock(final DefaultUnit unit, final Double size, final DefaultInternationalPrice prices, final BigDecimal overriddenPrice, Currency currency) {
        this.unit = unit;
        this.size = size;
        this.prices = prices != null ? new DefaultInternationalPrice(prices, overriddenPrice, currency) : null;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }

    public DefaultBlock setType(final BlockType type) {
        this.type = type;
        return this;
    }

    public DefaultBlock setPrices(final DefaultInternationalPrice prices) {
        this.prices = prices;
        return this;
    }

    public DefaultBlock setUnit(final DefaultUnit unit) {
        this.unit = unit;
        return this;
    }

    public DefaultBlock setSize(final Double size) {
        this.size = size;
        return this;
    }

    public DefaultBlock setPrice(final DefaultInternationalPrice prices) {
        this.prices = prices;
        return this;
    }

    public DefaultBlock setPhase(final PlanPhase phase) {
        this.phase = phase;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultBlock)) {
            return false;
        }

        final DefaultBlock that = (DefaultBlock) o;

        if (minTopUpCredit != null ? !minTopUpCredit.equals(that.minTopUpCredit) : that.minTopUpCredit != null) {
            return false;
        }
        if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
            return false;
        }
        if (prices != null ? !prices.equals(that.prices) : that.prices != null) {
            return false;
        }
        if (size != null ? !size.equals(that.size) : that.size != null) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        if (unit != null ? !unit.equals(that.unit) : that.unit != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (unit != null ? unit.hashCode() : 0);
        result = 31 * result + (size != null ? size.hashCode() : 0);
        result = 31 * result + (prices != null ? prices.hashCode() : 0);
        result = 31 * result + (minTopUpCredit != null ? minTopUpCredit.hashCode() : 0);
        return result;
    }
}
