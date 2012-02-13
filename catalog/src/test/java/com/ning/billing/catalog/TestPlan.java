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

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.util.config.ValidationErrors;

public class TestPlan {
	private static final Logger log = LoggerFactory.getLogger(TestPlan.class);
	@Test(groups={"fast"}, enabled = true)
	public void testDateValidation() {

		StandaloneCatalog c = new MockCatalog();
		c.setSupportedCurrencies(new Currency[]{Currency.GBP, Currency.EUR, Currency.USD, Currency.BRL, Currency.MXN});
		DefaultPlan p1 =  new MockPlan();
		p1.setEffectiveDateForExistingSubscriptons(new Date((new Date().getTime()) - (1000 * 60 * 60 * 24)));
		ValidationErrors errors = p1.validate(c, new ValidationErrors());
		Assert.assertEquals(errors.size(), 1);
		errors.log(log);

	}
	
	private static class MyDuration extends DefaultDuration {
		final int days;
		
		public MyDuration(int days) {
			this.days = days;
		}
		
		@Override
		public DateTime addToDateTime(DateTime dateTime) {
			return dateTime.plusDays(days);
		}
	}
	
	private static class MyPlanPhase extends MockPlanPhase {
		Duration duration;
		boolean recurringPriceIsZero;
		
		MyPlanPhase(int duration, boolean recurringPriceIsZero) {
			this.duration= new MyDuration( duration );
			this.recurringPriceIsZero = recurringPriceIsZero;
		}
		@Override
		public Duration getDuration(){
			return duration;
		}
		
		@Override
		public InternationalPrice getRecurringPrice() {
			return new MockInternationalPrice() {
				@Override
				public boolean isZero() {
					return recurringPriceIsZero;
				}
			};
		}
	}
	
	@Test(groups={"fast"}, enabled = true)
	public void testDataCalc() {
		DefaultPlan p0 =  new MockPlan() {
			public PlanPhase[] getAllPhases() {
				return new PlanPhase[]{
						new MyPlanPhase(10, true),
						new MyPlanPhase(10, false),
				};
			}
		};
		
		DefaultPlan p1 =  new MockPlan() {
			public PlanPhase[] getAllPhases() {
				return new PlanPhase[]{
						new MyPlanPhase(10, true),
						new MyPlanPhase(10, true),
						new MyPlanPhase(10, true),
						new MyPlanPhase(10, true),
						new MyPlanPhase(10, false),
						new MyPlanPhase(10, true),
				};
			}
		};
		
		DefaultPlan p2 =  new MockPlan() {
			public PlanPhase[] getAllPhases() {
				return new PlanPhase[]{
						new MyPlanPhase(10, false),
						new MyPlanPhase(10, true),
				};
			}
		};
		DateTime requestedDate = new DateTime();
		Assert.assertEquals(p0.dateOfFirstRecurringNonZeroCharge(requestedDate), requestedDate.plusDays(10));
		Assert.assertEquals(p1.dateOfFirstRecurringNonZeroCharge(requestedDate), requestedDate.plusDays(40));
		Assert.assertEquals(p2.dateOfFirstRecurringNonZeroCharge(requestedDate), requestedDate.plusDays(0));

	}
}
