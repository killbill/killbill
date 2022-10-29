/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Collections;

import javax.xml.bind.JAXBException;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
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
import org.killbill.xmlloader.ValidationException;
import org.killbill.xmlloader.XMLLoader;
import org.killbill.xmlloader.XMLWriter;

public class CatalogUpdater {

    public static String DEFAULT_CATALOG_NAME = "DEFAULT";
    
    public static String INVALID_PLAN = "Plan is invalid. Please check.";
    
    public static String INVALID_PRODUCT_NAME = "Please provide valid product name.";
    
    public static String INVALID_PRICE = "Please check amount and currency. Amount should be greater than 0 and currency should be valid.";
    
    public static String BASE_PLAN_PRODUCTS_NOT_EMPTY = "List of available base products should not be empty for add-ons.";
    
    public static String EXISTING_PRODUCTS_NOT_EMPTY = "Available base products contain invalid product.Please check.";
            

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
                .setProducts(Collections.emptyList())
                .setPlans(Collections.emptyList())
                .setPriceLists(new DefaultPriceListSet(defaultPriceList, new DefaultPriceList[0]))
                .setPlanRules(getSaneDefaultPlanRules(defaultPriceList));
        if (currencies != null && currencies.length > 0) {
            tmp.setSupportedCurrencies(currencies);
        } else {
            tmp.setSupportedCurrencies(new Currency[0]);
        }
        tmp.initialize(tmp);

        this.catalog = new DefaultMutableStaticCatalog(tmp);
    }

    public StandaloneCatalog getCatalog() {
        return catalog;
    }

    public String getCatalogXML(final InternalTenantContext internalTenantContext) throws CatalogApiException {
        try {
            final String newCatalog = XMLWriter.writeXML(catalog, StandaloneCatalog.class);
            // Verify we can deserialize this catalog prior we commit to disk
            XMLLoader.getObjectFromStream(new ByteArrayInputStream(newCatalog.getBytes()), StandaloneCatalog.class);
            return newCatalog;
        } catch (ValidationException e) {
            throw new CatalogApiException(e, ErrorCode.CAT_INVALID_FOR_TENANT, internalTenantContext.getTenantRecordId());
        } catch (JAXBException e) {
            throw new CatalogApiException(e, ErrorCode.CAT_INVALID_FOR_TENANT, internalTenantContext.getTenantRecordId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addSimplePlanDescriptor(final SimplePlanDescriptor desc) throws CatalogApiException {

        // We need at least a planId
        if (desc == null || desc.getPlanId() == null) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR , INVALID_PLAN);
        }

        DefaultPlan plan = (DefaultPlan) getExistingPlan(desc.getPlanId());
        if (plan == null && desc.getProductName() == null) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR, INVALID_PRODUCT_NAME);
        }

        validateNewPlanDescriptor(desc);

        DefaultProduct product = plan != null ? (DefaultProduct) plan.getProduct() : (DefaultProduct) getExistingProduct(desc.getProductName());
        if (product == null) {
            product = new DefaultProduct();
            product.setName(desc.getProductName());
            product.setCatagory(desc.getProductCategory());
            product.initialize(catalog);
            catalog.addProduct(product);
        }

        if (plan == null) {

            plan = new DefaultPlan();
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
            plan.initialize(catalog);
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
        catalog.initialize(catalog);
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
        if (catalog.getSupportedCurrencies() != null) {
            for (final Currency input : catalog.getSupportedCurrencies()) {
                if (input.equals(targetCurrency)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateNewPlanDescriptor(final SimplePlanDescriptor desc) throws CatalogApiException {
        final boolean invalidPlan = desc.getPlanId() == null && (desc.getProductCategory() == null || desc.getBillingPeriod() == null);
        final boolean invalidPrice = (desc.getAmount() == null || desc.getAmount().compareTo(BigDecimal.ZERO) < 0) ||
                                     desc.getCurrency() == null;
        if (invalidPlan || invalidPrice) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR,  INVALID_PRICE);
        }

        if (desc.getProductCategory() == ProductCategory.ADD_ON) {
            if (desc.getAvailableBaseProducts() == null || desc.getAvailableBaseProducts().isEmpty()) {
                throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR,  BASE_PLAN_PRODUCTS_NOT_EMPTY);
            }
            for (final String cur : desc.getAvailableBaseProducts()) {
                if (getExistingProduct(cur) == null) {
                    throw new CatalogApiException(ErrorCode.CAT_INVALID_SIMPLE_PLAN_DESCRIPTOR, EXISTING_PRODUCTS_NOT_EMPTY);
                }
            }
        }
    }

    private Product getExistingProduct(final String productName) {
        try {
            return catalog.findProduct(productName);
        } catch (final CatalogApiException e) {
            return null;
        }
    }

    private Plan getExistingPlan(final String planName) {
        try {
            return catalog.findPlan(planName);
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
