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
import java.util.Objects;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlTransient;

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
    private BigDecimal size;

    @XmlElement(required = true)
    private DefaultInternationalPrice prices;

    @XmlElement(required = false)
    private BigDecimal minTopUpCredit;

    // Not defined in catalog
    private PlanPhase phase;

    @XmlTransient
    private boolean minTopUpCreditHasValue;

    @Override
    public BlockType getType() {
        return type;
    }

    @Override
    public Unit getUnit() {
        return unit;
    }

    @Override
    public BigDecimal getSize() {
        return size;
    }

    @Override
    public InternationalPrice getPrice() {
        return prices;
    }

    @Override
    public BigDecimal getMinTopUpCredit() throws CatalogApiException {
        if (minTopUpCreditHasValue && type != BlockType.TOP_UP) {
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

        if (type == BlockType.TOP_UP && !minTopUpCreditHasValue) {
            errors.add(new ValidationError(String.format("TOP_UP block needs to define minTopUpCredit for phase %s",
                                                         phase.getName()), DefaultUsage.class, ""));
        }
        return errors;
    }

    public DefaultBlock() {
    }

    public DefaultBlock(final DefaultUnit unit, final BigDecimal size, final DefaultInternationalPrice prices, final BigDecimal overriddenPrice, final Currency currency) {
        this.unit = unit;
        this.size = size;
        this.prices = prices != null ? new DefaultInternationalPrice(prices, overriddenPrice, currency) : null;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
        minTopUpCreditHasValue = minTopUpCredit != null &&
                                 CatalogSafetyInitializer.DEFAULT_NON_REQUIRED_BIGDECIMAL_FIELD_VALUE.compareTo(minTopUpCredit) != 0;
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

    public DefaultBlock setSize(final BigDecimal size) {
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

        if (size.compareTo(that.size) != 0) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        if (!Objects.equals(unit, that.unit)) {
            return false;
        }
        if (!Objects.equals(prices, that.prices)) {
            return false;
        }
        return Objects.equals(minTopUpCredit, that.minTopUpCredit);
    }

    @Override
    public int hashCode() {
        int result;
        final long temp;
        result = type != null ? type.hashCode() : 0;
        result = 31 * result + (unit != null ? unit.hashCode() : 0);
        temp = size.hashCode();
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
        out.writeObject(size);
        out.writeObject(prices);
        out.writeBoolean(minTopUpCredit != null);
        if (minTopUpCredit != null) {
            out.writeObject(minTopUpCredit);
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.type = in.readBoolean() ? BlockType.valueOf(in.readUTF()) : null;
        this.unit = (DefaultUnit) in.readObject();
        this.size = (BigDecimal) in.readObject();
        this.prices = (DefaultInternationalPrice) in.readObject();
        this.minTopUpCredit = in.readBoolean() ? (BigDecimal) in.readObject() : null;
    }
}
