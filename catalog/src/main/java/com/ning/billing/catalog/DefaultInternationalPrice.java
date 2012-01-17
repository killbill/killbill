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

import java.math.BigDecimal;
import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.CurrencyValueNull;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Price;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultInternationalPrice extends ValidatingConfig<StandaloneCatalog> implements InternationalPrice {

	//TODO: Must have a price point for every configured currency
	//TODO: No prices is a zero cost plan
	@XmlElement(name="price")
	private DefaultPrice[] prices;


	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IInternationalPrice#getPrices()
	 */
	@Override
	public Price[] getPrices() {
		return prices;
	}



	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IInternationalPrice#getPrice(com.ning.billing.catalog.api.Currency)
	 */
	@Override 
	public BigDecimal getPrice(Currency currency) throws CatalogApiException {
		for(Price p : prices) {
			if(p.getCurrency() == currency) {
				return p.getValue();
			}
		}
		throw new CatalogApiException(ErrorCode.CAT_NO_PRICE_FOR_CURRENCY, currency);
	}

	protected DefaultInternationalPrice setPrices(DefaultPrice[] prices) {
		this.prices = prices;
		return this;
	}


	@Override
	public ValidationErrors validate(StandaloneCatalog catalog, ValidationErrors errors)  {
		Currency[] supportedCurrencies = catalog.getCurrentSupportedCurrencies();
		for (Price p : prices) {
			Currency currency = p.getCurrency();
			if(!currencyIsSupported(currency, supportedCurrencies)) {
				errors.add("Unsupported currency: " + currency, catalog.getCatalogURI(), this.getClass(), "");
			}
			try {
				if(p.getValue().doubleValue() < 0.0) {
					errors.add("Negative value for price in currency: " + currency, catalog.getCatalogURI(), this.getClass(), "");
				}
			} catch (CurrencyValueNull e) {
				// No currency => nothing to check, ignore exception
			}
		}
		return errors;
	}
	
	private boolean currencyIsSupported(Currency currency, Currency[] supportedCurrencies) {
		for (Currency c : supportedCurrencies) {
			if(c == currency) {
				return true;
			}
		}
		return false;
	}


	@Override
	public void initialize(StandaloneCatalog root, URI uri) {
		if(prices == null) {
			prices = getZeroPrice(root);
		}
		super.initialize(root, uri);
	}

	private synchronized DefaultPrice[] getZeroPrice(StandaloneCatalog root) {
		Currency[] currencies = root.getCurrentSupportedCurrencies();
		DefaultPrice[] zeroPrice = new DefaultPrice[currencies.length];
		for(int i = 0; i < currencies.length; i++) {
			zeroPrice[i] = new DefaultPrice();
			zeroPrice[i].setCurrency(currencies[i]);
			zeroPrice[i].setValue(new BigDecimal(0));
		}

		return zeroPrice;
	}

}
