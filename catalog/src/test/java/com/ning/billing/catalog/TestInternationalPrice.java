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
import java.net.URISyntaxException;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;

public class TestInternationalPrice {
  @Test
  public void testZeroValue() throws URISyntaxException {
	  Catalog c = new MockCatalog();
	  c.setSupportedCurrencies(new Currency[]{Currency.GBP, Currency.EUR, Currency.USD, Currency.BRL, Currency.MXN});
	  InternationalPrice p0 =  new MockInternationalPrice();
	  p0.setPrices(null);
	  p0.initialize(c, new URI("foo:bar"));
	  InternationalPrice p1 =  new MockInternationalPrice();
	  p1.setPrices(new Price[] {
			  new Price().setCurrency(Currency.GBP).setValue(new BigDecimal(1)),
			  new Price().setCurrency(Currency.EUR).setValue(new BigDecimal(1)),
			  new Price().setCurrency(Currency.USD).setValue(new BigDecimal(1)),
			  new Price().setCurrency(Currency.BRL).setValue(new BigDecimal(1)),
			  new Price().setCurrency(Currency.MXN).setValue(new BigDecimal(1)),		  
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
}
