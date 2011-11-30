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
import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.CurrencyValueNull;
import com.ning.billing.catalog.api.IInternationalPrice;
import com.ning.billing.catalog.api.IPrice;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class InternationalPrice extends ValidatingConfig<Catalog> implements IInternationalPrice {

	//TODO MDW Validation - effectiveDateForExistingSubscriptons > catalog effectiveDate 
	@XmlElement(required=false)
	private Date effectiveDateForExistingSubscriptons;

	//TODO: Must have a price point for every configured currency
	//TODO: No prices is a zero cost plan
	@XmlElement(name="price")
	private Price[] prices;


	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IInternationalPrice#getPrices()
	 */
	@Override
	public IPrice[] getPrices() {
		return prices;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IInternationalPrice#getEffectiveDateForExistingSubscriptons()
	 */
	@Override
	public Date getEffectiveDateForExistingSubscriptons() {
		return effectiveDateForExistingSubscriptons;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IInternationalPrice#getPrice(com.ning.billing.catalog.api.Currency)
	 */
	@Override 
	public BigDecimal getPrice(Currency currency) throws CatalogApiException {
		for(IPrice p : prices) {
			if(p.getCurrency() == currency) {
				return p.getValue();
			}
		}
		throw new CatalogApiException(ErrorCode.CAT_NO_PRICE_FOR_CURRENCY, currency);
	}


	protected void setEffectiveDateForExistingSubscriptons(
			Date effectiveDateForExistingSubscriptons) {
		this.effectiveDateForExistingSubscriptons = effectiveDateForExistingSubscriptons;
	}

	protected InternationalPrice setPrices(Price[] prices) {
		this.prices = prices;
		return this;
	}


	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors)  {
		Currency[] supportedCurrencies = catalog.getSupportedCurrencies();
		for (IPrice p : prices) {
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
		if(effectiveDateForExistingSubscriptons != null &&
				catalog.getEffectiveDate().getTime() > effectiveDateForExistingSubscriptons.getTime()) {
			errors.add(new ValidationError(String.format("Price effective date %s is before catalog effective date '%s'",
					effectiveDateForExistingSubscriptons,
					catalog.getEffectiveDate().getTime()), 
					catalog.getCatalogURI(), InternationalPrice.class, ""));
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
	public void initialize(Catalog root, URI uri) {
		if(prices == null) {
			prices = getZeroPrice(root);
		}
		super.initialize(root, uri);
	}

	private synchronized Price[] getZeroPrice(Catalog root) {
		Currency[] currencies = root.getSupportedCurrencies();
		Price[] zeroPrice = new Price[currencies.length];
		for(int i = 0; i < currencies.length; i++) {
			zeroPrice[i] = new Price();
			zeroPrice[i].setCurrency(currencies[i]);
			zeroPrice[i].setValue(new BigDecimal(0));
		}

		return zeroPrice;
	}

}
