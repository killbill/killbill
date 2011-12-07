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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.CurrencyValueNull;
import com.ning.billing.catalog.api.Price;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPrice extends ValidatingConfig<StandaloneCatalog> implements Price {
	@XmlElement(required=true)
	private Currency currency;

	@XmlElement(required=true,nillable=true)
	private BigDecimal value;

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPrice#getCurrency()
	 */
	@Override
	public Currency getCurrency() {
		return currency;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPrice#getValue()
	 */
	@Override
	public BigDecimal getValue() throws CurrencyValueNull {
		if (value == null) {
			throw new CurrencyValueNull(currency);
		}
		return value;
	}
	
	protected DefaultPrice setCurrency(Currency currency) {
		this.currency = currency;
		return this;
	}

	protected DefaultPrice setValue(BigDecimal value) {
		this.value = value;
		return this;
	}
	
	@Override
	public ValidationErrors validate(StandaloneCatalog catalog, ValidationErrors errors) {
		return errors;

	}
}
