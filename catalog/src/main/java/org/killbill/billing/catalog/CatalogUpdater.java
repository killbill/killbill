/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.catalog;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.SimplePlanDescriptor;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.rules.DefaultCaseBillingAlignment;
import org.killbill.billing.catalog.rules.DefaultCaseCancelPolicy;
import org.killbill.billing.catalog.rules.DefaultCaseChangePlanAlignment;
import org.killbill.billing.catalog.rules.DefaultCaseChangePlanPolicy;
import org.killbill.billing.catalog.rules.DefaultCaseCreateAlignment;
import org.killbill.billing.catalog.rules.DefaultCasePriceList;
import org.killbill.billing.catalog.rules.DefaultPlanRules;
import org.killbill.xmlloader.XMLWriter;

import com.google.common.collect.ImmutableList;

public class CatalogUpdater {

    public static String DEFAULT_CATALOG_NAME = "DEFAULT";

    private static URI DEFAULT_URI;

    static {
        try {
            DEFAULT_URI = new URI(DEFAULT_CATALOG_NAME);
        } catch (URISyntaxException e) {
        }
    }

    ;

    private final DefaultMutableStaticCatalog catalog;

    public CatalogUpdater(final StandaloneCatalog standaloneCatalog) {
        this.catalog = new DefaultMutableStaticCatalog(standaloneCatalog);
        this.catalog.setRecurringBillingMode(BillingMode.IN_ADVANCE);

    }

    public CatalogUpdater(final DateTime effectiveDate, final Currency... currencies) {

        final DefaultPriceList defaultPriceList = new DefaultPriceList().setName(PriceListSet.DEFAULT_PRICELIST_NAME);
        final StandaloneCatalog tmp = new StandaloneCatalog()
                .setCatalogName(DEFAULT_CATALOG_NAME)
                .setEffectiveDate(effectiveDate.toDate())
                .setRecurringBillingMode(BillingMode.IN_ADVANCE)
                .setProducts(ImmutableList.<Product>of())
                .setPlans(ImmutableList.<Plan>of())
                .setPriceLists(new DefaultPriceListSet(defaultPriceList, new DefaultPriceList[0]))
                .setPlanRules(getSaneDefaultPlanRules(defaultPriceList));
        if (currencies != null && currencies.length > 0) {
            tmp.setSupportedCurrencies(currencies);
        } else {
            tmp.setSupportedCurrencies(new Currency[0]);
        }
        tmp.initialize(tmp, DEFAULT_URI);

        this.catalog = new DefaultMutableStaticCatalog(tmp);
    }

    public StandaloneCatalog getCatalog() {
        return catalog;
    }

    public String getCatalogXML() {
        try {
            return XMLWriter.writeXML(catalog, StandaloneCatalog.class);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addSimplePlanDescriptor(final SimplePlanDescriptor desc) throws CatalogApiException {

        // We need at least a planId
        if (desc == null || desc.getPlanId() == null) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR, desc);
        }

        DefaultPlan plan = (DefaultPlan) getExistingPlan(desc.getPlanId());
        if (plan == null && desc.getProductName() == null) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR, desc);
        }

        validateNewPlanDescriptor(desc);

        DefaultProduct product = plan != null ? (DefaultProduct) plan.getProduct() : (DefaultProduct)  getExistingProduct(desc.getProductName());
        if (product == null) {
            product = new DefaultProduct();
            product.setName(desc.getProductName());
            product.setCatagory(desc.getProductCategory());
            product.initialize(catalog, DEFAULT_URI);
            catalog.addProduct(product);
        }

        if (plan == null) {

            plan = new DefaultPlan(catalog);
            plan.setName(desc.getPlanId());
            plan.setPriceListName(PriceListSet.DEFAULT_PRICELIST_NAME);
            plan.setProduct(product);
            plan.setRecurringBillingMode(catalog.getRecurringBillingMode());

            if (desc.getTrialLength() > 0 && desc.getTrialTimeUnit() != TimeUnit.UNLIMITED) {
                final DefaultPlanPhase trialPhase = new DefaultPlanPhase();
                trialPhase.setPhaseType(PhaseType.TRIAL);
                trialPhase.setDuration(new DefaultDuration().setUnit(desc.getTrialTimeUnit()).setNumber(desc.getTrialLength()));
                trialPhase.setFixed(new DefaultFixed().setFixedPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(desc.getCurrency()).setValue(BigDecimal.ZERO)})));
                plan.setInitialPhases(new DefaultPlanPhase[]{trialPhase});
            }
            catalog.addPlan(plan);
        } else {
            validateExistingPlan(plan, desc);
        }

        //
        // At this point we have an old or newly created **simple** Plan and we need to either create the recurring section or add a new currency.
        //
        if (!isCurrencySupported(desc.getCurrency())) {
            catalog.addCurrency(desc.getCurrency());
            // Reset the fixed price to null so the isZero() logic goes through new currencies and set the zero price for all
            if (plan.getInitialPhases().length == 1) {
                ((DefaultInternationalPrice) plan.getInitialPhases()[0].getFixed().getPrice()).setPrices(null);
            }
        }

        DefaultPlanPhase evergreenPhase = plan.getFinalPhase();
        if (evergreenPhase == null) {
            evergreenPhase = new DefaultPlanPhase();
            evergreenPhase.setPhaseType(PhaseType.EVERGREEN);
            evergreenPhase.setDuration(new DefaultDuration()
                                               .setUnit(TimeUnit.UNLIMITED));
            plan.setFinalPhase(evergreenPhase);
        }

        DefaultRecurring recurring = (DefaultRecurring) evergreenPhase.getRecurring();
        if (recurring == null) {
            recurring = new DefaultRecurring();
            recurring.setBillingPeriod(desc.getBillingPeriod());
            recurring.setRecurringPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[0]));
            evergreenPhase.setRecurring(recurring);
        }

        if (!isPriceForCurrencyExists(recurring.getRecurringPrice(), desc.getCurrency())) {
            catalog.addRecurringPriceToPlan(recurring.getRecurringPrice(), new DefaultPrice().setCurrency(desc.getCurrency()).setValue(desc.getAmount()));
        }

        if (desc.getProductCategory() == ProductCategory.ADD_ON) {
            for (final String bp : desc.getAvailableBaseProducts()) {
                final Product targetBasePlan = getExistingProduct(bp);
                boolean found = false;
                for (Product cur : targetBasePlan.getAvailable()) {
                    if (cur.getName().equals(product.getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    catalog.addProductAvailableAO(getExistingProduct(bp), product);
                }
            }
        }

        // Reinit catalog
        catalog.initialize(catalog, DEFAULT_URI);
    }

    private boolean isPriceForCurrencyExists(final InternationalPrice price, final Currency currency) {
        if (price.getPrices().length == 0) {
            return false;
        }
        try {
            price.getPrice(currency);
        } catch (CatalogApiException ignore) {
            return false;
        }
        return true;
    }

    private void validateExistingPlan(final DefaultPlan plan, final SimplePlanDescriptor desc) throws CatalogApiException {

        boolean failedValidation = false;

        //
        // TRIAL VALIDATION
        //
        // We only support adding new Plan with NO TRIAL or $0 TRIAL. Existing Plan not matching such criteria are incompatible
        if (plan.getInitialPhases().length > 1 ||
            (plan.getInitialPhases().length == 1 &&
             (plan.getInitialPhases()[0].getPhaseType() != PhaseType.TRIAL || !plan.getInitialPhases()[0].getFixed().getPrice().isZero()))) {
            failedValidation = true;

        } else if (desc.getTrialLength() != null && desc.getTrialTimeUnit() != null) { // If desc includes trial info we verify this is valid
            final boolean isDescConfiguredWithTrial = desc.getTrialLength() > 0 && desc.getTrialTimeUnit() != TimeUnit.UNLIMITED;
            final boolean isPlanConfiguredWithTrial = plan.getInitialPhases().length == 1;
            // Current plan has trial and desc does not or reverse
            if ((isDescConfiguredWithTrial && !isPlanConfiguredWithTrial) ||
                (!isDescConfiguredWithTrial && isPlanConfiguredWithTrial)) {
                failedValidation = true;
                // Both have trials , check they match
            } else if (isDescConfiguredWithTrial && isPlanConfiguredWithTrial) {
                if (plan.getInitialPhases()[0].getDuration().getUnit() != desc.getTrialTimeUnit() ||
                    plan.getInitialPhases()[0].getDuration().getNumber() != desc.getTrialLength()) {
                    failedValidation = true;
                }
            }
        }

        //
        // RECURRING VALIDATION
        //
        if (!failedValidation) {
            // Desc only supports EVERGREEN Phase
            if (plan.getFinalPhase().getPhaseType() != PhaseType.EVERGREEN) {
                failedValidation = true;
            } else {

                // Should be same recurring BillingPeriod
                if (desc.getBillingPeriod() != null && plan.getFinalPhase().getRecurring().getBillingPeriod() != desc.getBillingPeriod()) {
                    failedValidation = true;
                } else if (desc.getCurrency() != null && desc.getAmount() != null) {
                    try {
                        final BigDecimal currentAmount = plan.getFinalPhase().getRecurring().getRecurringPrice().getPrice(desc.getCurrency());
                        if (currentAmount.compareTo(desc.getAmount()) != 0) {
                            failedValidation = true;
                        }
                    } catch (CatalogApiException ignoreIfCurrencyIsCurrentlyUndefined) {
                    }
                }
            }
        }

        if (failedValidation) {
            throw new CatalogApiException(ErrorCode.CAT_FAILED_SIMPLE_PLAN_VALIDATION, plan.toString(), desc.toString());
        }
    }

    private boolean isCurrencySupported(final Currency targetCurrency) {
        if (catalog.getCurrentSupportedCurrencies() != null) {
            for (final Currency input : catalog.getCurrentSupportedCurrencies()) {
                if (input.equals(targetCurrency)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateNewPlanDescriptor(final SimplePlanDescriptor desc) throws CatalogApiException {
        if (desc.getProductCategory() == null ||
            desc.getBillingPeriod() == null ||
            (desc.getAmount() == null || desc.getAmount().compareTo(BigDecimal.ZERO) < 0) ||
            desc.getCurrency() == null) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR, desc);
        }

        if (desc.getProductCategory() == ProductCategory.ADD_ON) {
            if (desc.getAvailableBaseProducts() == null || desc.getAvailableBaseProducts().isEmpty()) {
                throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR, desc);
            }
            for (final String cur : desc.getAvailableBaseProducts()) {
                if (getExistingProduct(cur) == null) {
                    throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR, desc);
                }
            }
        }
    }

    private Product getExistingProduct(final String productName) {
        try {
            return catalog.findCurrentProduct(productName);
        } catch (final CatalogApiException e) {
            return null;
        }
    }

    private Plan getExistingPlan(final String planName) {
        try {
            return catalog.findCurrentPlan(planName);
        } catch (CatalogApiException e) {
            return null;
        }
    }

    private DefaultPlanRules getSaneDefaultPlanRules(final DefaultPriceList defaultPriceList) {

        final DefaultCaseChangePlanPolicy[] changePolicy = new DefaultCaseChangePlanPolicy[1];
        changePolicy[0] = new DefaultCaseChangePlanPolicy();
        changePolicy[0].setPolicy(BillingActionPolicy.IMMEDIATE);

        final DefaultCaseChangePlanAlignment[] changeAlignment = new DefaultCaseChangePlanAlignment[1];
        changeAlignment[0] = new DefaultCaseChangePlanAlignment();
        changeAlignment[0].setAlignment(PlanAlignmentChange.START_OF_BUNDLE);

        final DefaultCaseCancelPolicy[] cancelPolicy = new DefaultCaseCancelPolicy[1];
        cancelPolicy[0] = new DefaultCaseCancelPolicy();
        cancelPolicy[0].setPolicy(BillingActionPolicy.IMMEDIATE);

        final DefaultCaseCreateAlignment[] createAlignment = new DefaultCaseCreateAlignment[1];
        createAlignment[0] = new DefaultCaseCreateAlignment();
        createAlignment[0].setAlignment(PlanAlignmentCreate.START_OF_BUNDLE);

        final DefaultCaseBillingAlignment[] billingAlignmentCase = new DefaultCaseBillingAlignment[1];
        billingAlignmentCase[0] = new DefaultCaseBillingAlignment();
        billingAlignmentCase[0].setAlignment(BillingAlignment.ACCOUNT);

        final DefaultCasePriceList[] priceList = new DefaultCasePriceList[1];
        priceList[0] = new DefaultCasePriceList();
        priceList[0].setToPriceList(defaultPriceList);

        return new DefaultPlanRules()
                .setChangeCase(changePolicy)
                .setChangeAlignmentCase(changeAlignment)
                .setCancelCase(cancelPolicy)
                .setCreateAlignmentCase(createAlignment)
                .setBillingAlignmentCase(billingAlignmentCase)
                .setPriceListCase(priceList);
    }

}
