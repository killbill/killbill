/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.catalog;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.math.BigDecimal;
import java.net.URI;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.catalog.api.Price;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPrice extends ValidatingConfig<StandaloneCatalog> implements Price {
    @XmlElement(required = true)
    private Currency currency;

    @XmlElement(required = true, nillable = true)
    private BigDecimal value;

    public DefaultPrice() {
        // for serialization support
    }

    public DefaultPrice(final BigDecimal value, final Currency currency) {
        // for sanity support
        this.value = value;
        this.currency = currency;
    }

    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.IPrice#getCurrency()
      */
    @Override
    public Currency getCurrency() {
        return currency;
    }

    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.IPrice#getValue()
      */
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
        this.value = value;
        return this;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
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
}
