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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigDecimal;

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
public class DefaultBlock extends ValidatingConfig<StandaloneCatalog> implements Block, Externalizable {

    @XmlAttribute(required = false)
    private BlockType type = BlockType.VANILLA;

    @XmlElement(required = true)
    @XmlIDREF
    private DefaultUnit unit;

    @XmlElement(required = true)
    private double size;

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
                                                         phase.getName()), DefaultUsage.class, ""));
        }
        return errors;
    }

    public DefaultBlock() {
    }

    public DefaultBlock(final DefaultUnit unit, final double size, final DefaultInternationalPrice prices, final BigDecimal overriddenPrice, Currency currency) {
        this.unit = unit;
        this.size = size;
        this.prices = prices != null ? new DefaultInternationalPrice(prices, overriddenPrice, currency) : null;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
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

    public DefaultBlock setSize(final double size) {
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultBlock that = (DefaultBlock) o;

        if (Double.compare(that.size, size) != 0) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        if (unit != null ? !unit.equals(that.unit) : that.unit != null) {
            return false;
        }
        if (prices != null ? !prices.equals(that.prices) : that.prices != null) {
            return false;
        }
        if (minTopUpCredit != null ? !minTopUpCredit.equals(that.minTopUpCredit) : that.minTopUpCredit != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result;
        final long temp;
        result = type != null ? type.hashCode() : 0;
        result = 31 * result + (unit != null ? unit.hashCode() : 0);
        temp = Double.doubleToLongBits(size);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (prices != null ? prices.hashCode() : 0);
        result = 31 * result + (minTopUpCredit != null ? minTopUpCredit.hashCode() : 0);
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeBoolean(type != null);
        if (type != null) {
            out.writeUTF(type.name());
        }
        out.writeObject(unit);
        out.writeDouble(size);
        out.writeObject(prices);
        out.writeBoolean(minTopUpCredit != null);
        if (minTopUpCredit != null) {
            out.writeDouble(minTopUpCredit);
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.type = in.readBoolean() ? BlockType.valueOf(in.readUTF()) : null;
        this.unit = (DefaultUnit) in.readObject();
        this.size = in.readDouble();
        this.prices = (DefaultInternationalPrice) in.readObject();
        this.minTopUpCredit = in.readBoolean() ? in.readDouble() : null;
    }
}
