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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.catalog.api.Price;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPrice extends ValidatingConfig<StandaloneCatalog> implements Price, Externalizable {

    private static final Map<String, BigDecimal> frequentValues = new ConcurrentHashMap<String, BigDecimal>();
    private static final int FREQUENT_VALUES_CACHE_SIZE = Integer.parseInt(System.getProperty("org.killbill.catalog.frequentValuesCacheSize", "1000"));

    @XmlElement(required = true)
    private Currency currency;

    private BigDecimal value;

    public DefaultPrice() {
        // for serialization support
    }

    public DefaultPrice(final BigDecimal value, final Currency currency) {
        // for sanity support
        this.value = value;
        this.currency = currency;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @XmlElement(required = true, nillable = true)
    @Override
    public BigDecimal getValue() throws CurrencyValueNull {
        if (value == null) {
            throw new CurrencyValueNull(currency);
        }
        return value;
    }

    public DefaultPrice setCurrency(final Currency currency) {
        this.currency = currency;
        return this;
    }

    public DefaultPrice setValue(final BigDecimal value) {
        if (value == null) {
            this.value = value;
            return this;
        }

        final String valueAsString = value.toString();

        if (!frequentValues.containsKey(valueAsString) && frequentValues.size() < FREQUENT_VALUES_CACHE_SIZE) {
            frequentValues.put(valueAsString, value);
        }

        if (frequentValues.containsKey(valueAsString)) {
            this.value = frequentValues.get(valueAsString);
        } else {
            this.value = value;
        }
        return this;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPrice)) {
            return false;
        }

        final DefaultPrice that = (DefaultPrice) o;

        if (currency != that.currency) {
            return false;
        }
        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = currency != null ? currency.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeBoolean(currency != null);
        if (currency != null) {
            out.writeUTF(currency.name());
        }
        out.writeObject(value);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.currency = in.readBoolean() ? Currency.valueOf(in.readUTF()) : null;
        this.value = (BigDecimal) in.readObject();
    }
}
