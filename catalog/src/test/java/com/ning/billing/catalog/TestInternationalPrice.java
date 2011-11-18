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
