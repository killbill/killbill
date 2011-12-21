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

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.util.config.ValidationErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestPlanPhase {
	Logger log = LoggerFactory.getLogger(TestPlanPhase.class);
	
	@Test(enabled=true)
	public void testValidation() {
		log.info("Testing Plan Phase Validation");
		
		DefaultPlanPhase pp = new MockPlanPhase().setBillCycleDuration(BillingPeriod.MONTHLY).setReccuringPrice(null).setFixedPrice(new DefaultInternationalPrice());
		ValidationErrors errors = pp.validate(new MockCatalog(), new ValidationErrors());
		errors.log(log);
		Assert.assertEquals(errors.size(), 1);

		pp = new MockPlanPhase().setBillCycleDuration(BillingPeriod.NO_BILLING_PERIOD).setReccuringPrice(new MockInternationalPrice());
		errors = pp.validate(new MockCatalog(), new ValidationErrors());
		errors.log(log);
		Assert.assertEquals(errors.size(), 1);

		pp = new MockPlanPhase().setReccuringPrice(null).setFixedPrice(null).setBillCycleDuration(BillingPeriod.NO_BILLING_PERIOD);
		errors = pp.validate(new MockCatalog(), new ValidationErrors());
		errors.log(log);
		Assert.assertEquals(errors.size(), 1);
}
}
