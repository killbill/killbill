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

import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.Price;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultInternationalPrice extends ValidatingConfig<StandaloneCatalog> implements InternationalPrice {

    // No prices is a zero cost plan in all currencies
    @XmlElement(name = "price", required = false)
    private DefaultPrice[] prices;


    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.InternationalPrice#getPrices()
      */
    @Override
    public Price[] getPrices() {
        return prices;
    }

    public DefaultInternationalPrice() {}

    public DefaultInternationalPrice(final DefaultInternationalPrice in, final PlanPhasePriceOverride override, final boolean fixed) {

        if (in.getPrices().length == 0) {
            this.prices = new DefaultPrice[1];
            this.prices[0] = new DefaultPrice(fixed ? override.getFixedPrice() : override.getRecurringPrice(), override.getCurrency());
        } else {
            this.prices = new DefaultPrice[in.getPrices().length];
            // There is a question on whether we keep the other prices that were not overridden or only have one entry for the overridden price on that currency.
            for (int i = 0; i < in.getPrices().length; i++) {
                final DefaultPrice curPrice = (DefaultPrice)  in.getPrices()[i];
                if (curPrice.getCurrency().equals(override.getCurrency())) {
                    prices[i] = new DefaultPrice(fixed ? override.getFixedPrice() : override.getRecurringPrice(), override.getCurrency());
                } else {
                    prices[i] = curPrice;
                }
            }
        }
    }

    public DefaultInternationalPrice(final DefaultInternationalPrice in, final BigDecimal overriddenPrice, final Currency currency) {
        this.prices = in.getPrices() != null ? new DefaultPrice[in.getPrices().length] : null;
        for (int i = 0; i < in.getPrices().length; i++) {
            final DefaultPrice curPrice = (DefaultPrice)  in.getPrices()[i];
            if (curPrice.getCurrency().equals(currency)){
                prices[i] = new DefaultPrice(overriddenPrice, currency);
            } else {
                prices[i] = curPrice;
            }
        }
    }

    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.IInternationalPrice#getPrice(org.killbill.billing.catalog.api.Currency)
      */
    @Override
    public BigDecimal getPrice(final Currency currency) throws CatalogApiException {
        if (prices.length == 0) {
            return BigDecimal.ZERO;
        }

        for (final Price p : prices) {
            if (p.getCurrency() == currency) {
                return p.getValue();
            }
        }
        throw new CatalogApiException(ErrorCode.CAT_NO_PRICE_FOR_CURRENCY, currency);
    }

    public DefaultInternationalPrice setPrices(final DefaultPrice[] prices) {
        this.prices = prices;
        return this;
    }


    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        final Currency[] supportedCurrencies = catalog.getCurrentSupportedCurrencies();
        for (final Price p : prices) {
            final Currency currency = p.getCurrency();
            if (!currencyIsSupported(currency, supportedCurrencies)) {
                errors.add("Unsupported currency: " + currency, catalog.getCatalogURI(), this.getClass(), "");
            }
            try {
                if (p.getValue().doubleValue() < 0.0) {
                    errors.add("Negative value for price in currency: " + currency, catalog.getCatalogURI(), this.getClass(), "");
                }
            } catch (CurrencyValueNull e) {
                // No currency => nothing to check, ignore exception
            }
        }
        return errors;
    }

    private boolean currencyIsSupported(final Currency currency, final Currency[] supportedCurrencies) {
        for (final Currency c : supportedCurrencies) {
            if (c == currency) {
                return true;
            }
        }
        return false;
    }


    @Override
    public void initialize(final StandaloneCatalog root, final URI uri) {
        super.initialize(root, uri);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }


    @Override
    public boolean isZero() {
        for (final DefaultPrice price : prices) {
            try {
                if (price.getValue().compareTo(BigDecimal.ZERO) != 0) {
                    return false;
                }
            } catch (CurrencyValueNull e) {
                //Ignore if the currency is null we treat it as 0
            }
        }
        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultInternationalPrice)) {
            return false;
        }

        final DefaultInternationalPrice that = (DefaultInternationalPrice) o;

        if (!Arrays.equals(prices, that.prices)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return prices != null ? Arrays.hashCode(prices) : 0;
    }
}
