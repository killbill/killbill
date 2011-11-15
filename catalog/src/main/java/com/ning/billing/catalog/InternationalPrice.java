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
import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.IInternationalPrice;
import com.ning.billing.catalog.api.IPrice;
import com.ning.billing.util.config.ValidatingConfig;
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
	public BigDecimal getPrice(Currency currency) {
		// Note if there are no prices specified we default to 0 for any currency
		for(IPrice p : prices) {
			if(p.getCurrency() == currency) {
				return p.getValue();
			}
		}
		return new BigDecimal(0);
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors)  {
		if(prices.length == 0) return errors;
		Currency[] supportedCurrencies = catalog.getSupportedCurrencies();
		for (IPrice p : prices) {
			Currency currency = p.getCurrency();
			if(!currencyIsSupported(currency, supportedCurrencies)) {
				errors.add("Unsupported currency: " + currency, catalog.getCatalogURI(), this.getClass(), "");
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
	
	protected void setEffectiveDateForExistingSubscriptons(
			Date effectiveDateForExistingSubscriptons) {
		this.effectiveDateForExistingSubscriptons = effectiveDateForExistingSubscriptons;
	}

	protected InternationalPrice setPrices(Price[] prices) {
		this.prices = prices;
		return this;
	}

}
