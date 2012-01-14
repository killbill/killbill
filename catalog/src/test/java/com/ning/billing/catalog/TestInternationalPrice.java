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

import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.config.ValidationErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

public class TestInternationalPrice {
	private static final Logger log = LoggerFactory.getLogger(TestInternationalPrice.class);
	
  @Test
  public void testZeroValue() throws URISyntaxException, CatalogApiException {
	  StandaloneCatalog c = new MockCatalog();
	  c.setSupportedCurrencies(new Currency[]{Currency.GBP, Currency.EUR, Currency.USD, Currency.BRL, Currency.MXN});
	  DefaultInternationalPrice p0 =  new MockInternationalPrice();
	  p0.setPrices(null);
	  p0.initialize(c, new URI("foo:bar"));
	  DefaultInternationalPrice p1 =  new MockInternationalPrice();
	  p1.setPrices(new DefaultPrice[] {
			  new DefaultPrice().setCurrency(Currency.GBP).setValue(new BigDecimal(1)),
			  new DefaultPrice().setCurrency(Currency.EUR).setValue(new BigDecimal(1)),
			  new DefaultPrice().setCurrency(Currency.USD).setValue(new BigDecimal(1)),
			  new DefaultPrice().setCurrency(Currency.BRL).setValue(new BigDecimal(1)),
			  new DefaultPrice().setCurrency(Currency.MXN).setValue(new BigDecimal(1)),		  
	  });
	  p1.initialize(c, new URI("foo:bar"));

	  Assert.assertEquals(p0.getPrice(Currency.GBP), new BigDecimal(0));
	  Assert.assertEquals(p0.getPrice(Currency.EUR), new BigDecimal(0));
	  Assert.assertEquals(p0.getPrice(Currency.USD), new BigDecimal(0));
	  Assert.assertEquals(p0.getPrice(Currency.BRL), new BigDecimal(0));
	  Assert.assertEquals(p0.getPrice(Currency.MXN), new BigDecimal(0));
	  
	  Assert.assertEquals(p1.getPrice(Currency.GBP), new BigDecimal(1));
	  Assert.assertEquals(p1.getPrice(Currency.EUR), new BigDecimal(1));
	  Assert.assertEquals(p1.getPrice(Currency.USD), new BigDecimal(1));
	  Assert.assertEquals(p1.getPrice(Currency.BRL), new BigDecimal(1));
	  Assert.assertEquals(p1.getPrice(Currency.MXN), new BigDecimal(1));
	  
  }
  
  @Test
  public void testPriceInitialization() throws URISyntaxException, CatalogApiException  {
	  StandaloneCatalog c = new MockCatalog();
	  c.setSupportedCurrencies(new Currency[]{Currency.GBP, Currency.EUR, Currency.USD, Currency.BRL, Currency.MXN});
	  c.initialize(c, new URI("foo://bar"));
	  Assert.assertEquals(c.getCurrentPlans()[0].getFinalPhase().getRecurringPrice().getPrice(Currency.GBP), new BigDecimal(0));
  }
  
  @Test
  public void testNegativeValuePrices(){
	  StandaloneCatalog c = new MockCatalog();
	  c.setSupportedCurrencies(new Currency[]{Currency.GBP, Currency.EUR, Currency.USD, Currency.BRL, Currency.MXN});
	
	  DefaultInternationalPrice p1 =  new MockInternationalPrice();
	  p1.setPrices(new DefaultPrice[] {
			  new DefaultPrice().setCurrency(Currency.GBP).setValue(new BigDecimal(-1)),
			  new DefaultPrice().setCurrency(Currency.EUR).setValue(new BigDecimal(-1)),
			  new DefaultPrice().setCurrency(Currency.USD).setValue(new BigDecimal(-1)),
			  new DefaultPrice().setCurrency(Currency.BRL).setValue(new BigDecimal(1)),
			  new DefaultPrice().setCurrency(Currency.MXN).setValue(new BigDecimal(1)),		  
	  });
	  
	 ValidationErrors errors = p1.validate(c, new ValidationErrors());
	 errors.log(log);
	 Assert.assertEquals(errors.size(), 3);
  }

  
  
  
}
