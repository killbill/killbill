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

package com.ning.billing.entitlement.api.billing;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.entitlement.api.user.SubscriptionTransition.SubscriptionTransitionType;

public class BillCycleDayCalculator {
	private static final Logger log = LoggerFactory.getLogger(BillCycleDayCalculator.class);
	
	private final AccountUserApi accountApi;
	private final CatalogService catalogService;

	@Inject
	public BillCycleDayCalculator(final AccountUserApi accountApi, final CatalogService catalogService) {
		super();
		this.accountApi = accountApi;
		this.catalogService = catalogService;
	}

	protected int calculateBcd(SubscriptionBundle bundle, Subscription subscription, final SubscriptionTransition transition, final UUID accountId) throws CatalogApiException, AccountApiException {
		Catalog catalog = catalogService.getFullCatalog();
		Plan plan =  (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
				transition.getNextPlan() : transition.getPreviousPlan();
				Product product = plan.getProduct();
				PlanPhase phase = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
						transition.getNextPhase() : transition.getPreviousPhase();

						BillingAlignment alignment = catalog.billingAlignment(
								new PlanPhaseSpecifier(product.getName(),
										product.getCategory(),
										phase.getBillingPeriod(),
										transition.getNextPriceList(),
										phase.getPhaseType()),
										transition.getRequestedTransitionTime());
						int result = -1;

						Account account = accountApi.getAccountById(accountId);
						switch (alignment) {
						case ACCOUNT :
							result = account.getBillCycleDay();

							if(result == 0) {
								result = calculateBcdFromSubscription(subscription, plan, account);
							}
							break;
						case BUNDLE :
							result = bundle.getStartDate().toDateTime(account.getTimeZone()).getDayOfMonth();
							break;
						case SUBSCRIPTION :
							result = subscription.getStartDate().toDateTime(account.getTimeZone()).getDayOfMonth();
							break;
						}
						if(result == -1) {
							throw new CatalogApiException(ErrorCode.CAT_INVALID_BILLING_ALIGNMENT, alignment.toString());
						}
						return result;

	}

	private int calculateBcdFromSubscription(Subscription subscription, Plan plan, Account account) throws AccountApiException {
		int result = account.getBillCycleDay();
		if(result != 0) {
			return result;
		}
		result = new DateTime(account.getTimeZone()).getDayOfMonth();

		try {
			result = billCycleDay(subscription.getStartDate(),account.getTimeZone(), plan);
		} catch (CatalogApiException e) {
			log.error("Unexpected catalog error encountered when updating BCD",e);
		}


		Account modifiedAccount = new DefaultAccount(
				account.getId(),
				account.getExternalKey(),
				account.getEmail(),
				account.getName(),
				account.getFirstNameLength(),
				account.getCurrency(),
				result,
				account.getPaymentProviderName(),
				account.getTimeZone(),
				account.getLocale(),
				account.getAddress1(),
				account.getAddress2(),
				account.getCompanyName(),
				account.getCity(),
				account.getStateOrProvince(),
				account.getCountry(),
				account.getPostalCode(),
				account.getPhone(),
				account.getCreatedDate(),
				null // Updated date will be set internally
				);
		accountApi.updateAccount(modifiedAccount);
		return result;
	}

	private int billCycleDay(DateTime requestedDate, DateTimeZone timeZone, 
			Plan plan) throws CatalogApiException {

		DateTime date = plan.dateOfFirstRecurringNonZeroCharge(requestedDate);
		return date.toDateTime(timeZone).getDayOfMonth();

	}

}
