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

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.config.ValidationErrors;

public class TestPlan {
	private static final Logger log = LoggerFactory.getLogger(TestPlan.class);
	@Test
	public void testDateValidation() {

		StandaloneCatalog c = new MockCatalog();
		c.setSupportedCurrencies(new Currency[]{Currency.GBP, Currency.EUR, Currency.USD, Currency.BRL, Currency.MXN});
		DefaultPlan p1 =  new MockPlan();
		p1.setEffectiveDateForExistingSubscriptons(new Date((new Date().getTime()) - (1000 * 60 * 60 * 24)));
		ValidationErrors errors = p1.validate(c, new ValidationErrors());
		Assert.assertEquals(errors.size(), 1);
		errors.log(log);

	}
}
