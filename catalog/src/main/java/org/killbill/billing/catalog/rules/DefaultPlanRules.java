/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.catalog.rules;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.killbill.billing.catalog.CatalogSafetyInitializer;
import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.IllegalPlanChange;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.rules.CaseBillingAlignment;
import org.killbill.billing.catalog.api.rules.CaseCancelPolicy;
import org.killbill.billing.catalog.api.rules.CaseChangePlanAlignment;
import org.killbill.billing.catalog.api.rules.CaseChangePlanPolicy;
import org.killbill.billing.catalog.api.rules.CaseCreateAlignment;
import org.killbill.billing.catalog.api.rules.CasePriceList;
import org.killbill.billing.catalog.api.rules.PlanRules;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

import com.google.common.collect.ImmutableList;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPlanRules extends ValidatingConfig<StandaloneCatalog> implements PlanRules {

    @XmlElementWrapper(name = "changePolicy")
    @XmlElement(name = "changePolicyCase", required = false)
    private DefaultCaseChangePlanPolicy[] changeCase;

    @XmlElementWrapper(name = "changeAlignment")
    @XmlElement(name = "changeAlignmentCase", required = false)
    private DefaultCaseChangePlanAlignment[] changeAlignmentCase;

    @XmlElementWrapper(name = "cancelPolicy")
    @XmlElement(name = "cancelPolicyCase", required = false)
    private DefaultCaseCancelPolicy[] cancelCase;

    @XmlElementWrapper(name = "createAlignment")
    @XmlElement(name = "createAlignmentCase", required = false)
    private DefaultCaseCreateAlignment[] createAlignmentCase;

    @XmlElementWrapper(name = "billingAlignment")
    @XmlElement(name = "billingAlignmentCase", required = false)
    private DefaultCaseBillingAlignment[] billingAlignmentCase;

    @XmlElementWrapper(name = "priceList")
    @XmlElement(name = "priceListCase", required = false)
    private DefaultCasePriceList[] priceListCase;

    @Override
    public Iterable<CaseChangePlanPolicy> getCaseChangePlanPolicy() {
        return ImmutableList.<CaseChangePlanPolicy>copyOf(changeCase);
    }

    @Override
    public Iterable<CaseChangePlanAlignment> getCaseChangePlanAlignment() {
        return ImmutableList.<CaseChangePlanAlignment>copyOf(changeAlignmentCase);
    }

    @Override
    public Iterable<CaseCancelPolicy> getCaseCancelPolicy() {
        return ImmutableList.<CaseCancelPolicy>copyOf(cancelCase);
    }

    @Override
    public Iterable<CaseCreateAlignment> getCaseCreateAlignment() {
        return ImmutableList.<CaseCreateAlignment>copyOf(createAlignmentCase);
    }

    @Override
    public Iterable<CaseBillingAlignment> getCaseBillingAlignment() {
        return ImmutableList.<CaseBillingAlignment>copyOf(billingAlignmentCase);
    }

    @Override
    public Iterable<CasePriceList> getCasePriceList() {
        return ImmutableList.<CasePriceList>copyOf(priceListCase);
    }

    public PlanAlignmentCreate getPlanCreateAlignment(final PlanSpecifier specifier, final StaticCatalog catalog) throws CatalogApiException {
        final PlanAlignmentCreate result = DefaultCase.getResult(createAlignmentCase, specifier, catalog);
        return (result != null) ? result : PlanAlignmentCreate.START_OF_BUNDLE;
    }

    public BillingActionPolicy getPlanCancelPolicy(final PlanPhaseSpecifier planPhase, final StaticCatalog catalog) throws CatalogApiException {
        final BillingActionPolicy result = DefaultCasePhase.getResult(cancelCase, planPhase, catalog);
        return (result != null) ? result : BillingActionPolicy.END_OF_TERM;
    }

    public BillingAlignment getBillingAlignment(final PlanPhaseSpecifier planPhase, final StaticCatalog catalog) throws CatalogApiException {
        final BillingAlignment result = DefaultCasePhase.getResult(billingAlignmentCase, planPhase, catalog);
        return (result != null) ? result : BillingAlignment.ACCOUNT;
    }

    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to, final StaticCatalog catalog) throws CatalogApiException {

        final DefaultPriceList toPriceList = to.getPriceListName() != null ?
                                             (DefaultPriceList) catalog.findCurrentPricelist(to.getPriceListName()) :
                                             findPriceList(from, catalog);

        // If we use old scheme {product, billingPeriod, pricelist}, ensure pricelist is correct
        // (Pricelist may be null because if it is unspecified this is the principal use-case)
        final PlanSpecifier toWithPriceList = to.getPlanName() == null ?
                                              new PlanSpecifier(to.getProductName(), to.getBillingPeriod(), toPriceList.getName()) :
                                              to;


        final BillingActionPolicy policy = getPlanChangePolicy(from, toWithPriceList, catalog);
        if (policy == BillingActionPolicy.ILLEGAL) {
            throw new IllegalPlanChange(from, toWithPriceList);
        }

        final PlanAlignmentChange alignment = getPlanChangeAlignment(from, toWithPriceList, catalog);

        return new PlanChangeResult(toPriceList, policy, alignment);
    }

    private PlanAlignmentChange getPlanChangeAlignment(final PlanPhaseSpecifier from,
                                                       final PlanSpecifier to, final StaticCatalog catalog) throws CatalogApiException {
        final PlanAlignmentChange result = DefaultCaseChange.getResult(changeAlignmentCase, from, to, catalog);
        return (result != null) ? result : PlanAlignmentChange.START_OF_BUNDLE;
    }

    private BillingActionPolicy getPlanChangePolicy(final PlanPhaseSpecifier from,
                                                    final PlanSpecifier to, final StaticCatalog catalog) throws CatalogApiException {
        final BillingActionPolicy result = DefaultCaseChange.getResult(changeCase, from, to, catalog);
        return (result != null) ? result : BillingActionPolicy.END_OF_TERM;
    }

    private DefaultPriceList findPriceList(final PlanSpecifier specifier, final StaticCatalog catalog) throws CatalogApiException {
        DefaultPriceList result = DefaultCasePriceList.getResult(priceListCase, specifier, catalog);
        if (result == null) {
            final String priceListName = specifier.getPlanName() != null ? catalog.findCurrentPlan(specifier.getPlanName()).getPriceListName() : specifier.getPriceListName();
            result = (DefaultPriceList) catalog.findCurrentPricelist(priceListName);
        }
        return result;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        //
        // Validate that there is a default policy for change AND cancel rules and check unicity of rules
        //
        final HashSet<DefaultCaseChangePlanPolicy> caseChangePlanPoliciesSet = new HashSet<DefaultCaseChangePlanPolicy>();
        boolean foundDefaultCase = false;
        for (final DefaultCaseChangePlanPolicy cur : changeCase) {
            if (caseChangePlanPoliciesSet.contains(cur)) {
                errors.add(new ValidationError(String.format("Duplicate rule for change plan %s", cur.toString()), catalog.getCatalogURI(), DefaultPlanRules.class, ""));
            } else {
                caseChangePlanPoliciesSet.add(cur);
            }
            if (cur.getPhaseType() == null &&
                cur.getFromProduct() == null &&
                cur.getFromProductCategory() == null &&
                cur.getFromBillingPeriod() == null &&
                cur.getFromPriceList() == null &&
                cur.getToProduct() == null &&
                cur.getToProductCategory() == null &&
                cur.getToBillingPeriod() == null &&
                cur.getToPriceList() == null) {
                foundDefaultCase = true;
            }
            cur.validate(catalog, errors);
        }
        if (!foundDefaultCase) {
            errors.add(new ValidationError("Missing default rule case for plan change", catalog.getCatalogURI(), DefaultPlanRules.class, ""));
        }

        final HashSet<DefaultCaseCancelPolicy> defaultCaseCancelPoliciesSet = new HashSet<DefaultCaseCancelPolicy>();
        foundDefaultCase = false;
        for (final DefaultCaseCancelPolicy cur : cancelCase) {
            if (defaultCaseCancelPoliciesSet.contains(cur)) {
                errors.add(new ValidationError(String.format("Duplicate rule for plan cancellation %s", cur.toString()), catalog.getCatalogURI(), DefaultPlanRules.class, ""));
            } else {
                defaultCaseCancelPoliciesSet.add(cur);
            }
            if (cur.getPhaseType() == null &&
                cur.getProduct() == null &&
                cur.getProductCategory() == null &&
                cur.getBillingPeriod() == null &&
                cur.getPriceList() == null) {
                foundDefaultCase = true;
            }
            cur.validate(catalog, errors);
        }
        if (!foundDefaultCase) {
            errors.add(new ValidationError("Missing default rule case for plan cancellation", catalog.getCatalogURI(), DefaultPlanRules.class, ""));
        }


        final HashSet<DefaultCaseChangePlanAlignment> caseChangePlanAlignmentsSet = new HashSet<DefaultCaseChangePlanAlignment>();
        for (final DefaultCaseChangePlanAlignment cur : changeAlignmentCase) {
            if (caseChangePlanAlignmentsSet.contains(cur)) {
                errors.add(new ValidationError(String.format("Duplicate rule for plan change alignment %s", cur.toString()), catalog.getCatalogURI(), DefaultPlanRules.class, ""));
            } else {
                caseChangePlanAlignmentsSet.add(cur);
            }
            cur.validate(catalog, errors);
        }

        final HashSet<DefaultCaseCreateAlignment> caseCreateAlignmentsSet = new HashSet<DefaultCaseCreateAlignment>();
        for (final DefaultCaseCreateAlignment cur : createAlignmentCase) {
            if (caseCreateAlignmentsSet.contains(cur)) {
                errors.add(new ValidationError(String.format("Duplicate rule for create plan alignment %s", cur.toString()), catalog.getCatalogURI(), DefaultPlanRules.class, ""));
            } else {
                caseCreateAlignmentsSet.add(cur);
            }
            cur.validate(catalog, errors);
        }

        final HashSet<DefaultCaseBillingAlignment> caseBillingAlignmentsSet = new HashSet<DefaultCaseBillingAlignment>();
        for (final DefaultCaseBillingAlignment cur : billingAlignmentCase) {
            if (caseBillingAlignmentsSet.contains(cur)) {
                errors.add(new ValidationError(String.format("Duplicate rule for billing alignment %s", cur.toString()), catalog.getCatalogURI(), DefaultPlanRules.class, ""));
            } else {
                caseBillingAlignmentsSet.add(cur);
            }
            cur.validate(catalog, errors);
        }

        final HashSet<DefaultCasePriceList> casePriceListsSet = new HashSet<DefaultCasePriceList>();
        for (final DefaultCasePriceList cur : priceListCase) {
            if (casePriceListsSet.contains(cur)) {
                errors.add(new ValidationError(String.format("Duplicate rule for price list transition %s", cur.toString()), catalog.getCatalogURI(), DefaultPlanRules.class, ""));
            } else {
                casePriceListsSet.add(cur);
            }
            cur.validate(catalog, errors);
        }
        return errors;
    }


    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);

        for (final DefaultCaseChangePlanPolicy cur : changeCase) {
            cur.initialize(catalog, sourceURI);
        }
        for (final DefaultCaseChangePlanAlignment cur : changeAlignmentCase) {
            cur.initialize(catalog, sourceURI);
        }
        for (final DefaultCaseCancelPolicy cur : cancelCase) {
            cur.initialize(catalog, sourceURI);
        }
        for (final DefaultCaseCreateAlignment cur : createAlignmentCase) {
            cur.initialize(catalog, sourceURI);
        }
        for (final DefaultCaseBillingAlignment cur : billingAlignmentCase) {
            cur.initialize(catalog, sourceURI);
        }
        for (final DefaultCasePriceList cur : priceListCase) {
            cur.initialize(catalog, sourceURI);
        }
    }


    /////////////////////////////////////////////////////////////////////////////////////
    // Setters for testing
    /////////////////////////////////////////////////////////////////////////////////////

    public DefaultPlanRules setChangeCase(final DefaultCaseChangePlanPolicy[] changeCase) {
        this.changeCase = changeCase;
        return this;
    }

    public DefaultPlanRules setChangeAlignmentCase(
            final DefaultCaseChangePlanAlignment[] changeAlignmentCase) {
        this.changeAlignmentCase = changeAlignmentCase;
        return this;
    }

    public DefaultPlanRules setCancelCase(final DefaultCaseCancelPolicy[] cancelCase) {
        this.cancelCase = cancelCase;
        return this;
    }

    public DefaultPlanRules setCreateAlignmentCase(final DefaultCaseCreateAlignment[] createAlignmentCase) {
        this.createAlignmentCase = createAlignmentCase;
        return this;
    }

    public DefaultPlanRules setBillingAlignmentCase(
            final DefaultCaseBillingAlignment[] billingAlignmentCase) {
        this.billingAlignmentCase = billingAlignmentCase;
        return this;
    }

    public DefaultPlanRules setPriceListCase(final DefaultCasePriceList[] priceListCase) {
        this.priceListCase = priceListCase;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPlanRules)) {
            return false;
        }

        final DefaultPlanRules that = (DefaultPlanRules) o;

        if (!Arrays.equals(billingAlignmentCase, that.billingAlignmentCase)) {
            return false;
        }
        if (!Arrays.equals(cancelCase, that.cancelCase)) {
            return false;
        }
        if (!Arrays.equals(changeAlignmentCase, that.changeAlignmentCase)) {
            return false;
        }
        if (!Arrays.equals(changeCase, that.changeCase)) {
            return false;
        }
        if (!Arrays.equals(createAlignmentCase, that.createAlignmentCase)) {
            return false;
        }
        if (!Arrays.equals(priceListCase, that.priceListCase)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = changeCase != null ? Arrays.hashCode(changeCase) : 0;
        result = 31 * result + (changeAlignmentCase != null ? Arrays.hashCode(changeAlignmentCase) : 0);
        result = 31 * result + (cancelCase != null ? Arrays.hashCode(cancelCase) : 0);
        result = 31 * result + (createAlignmentCase != null ? Arrays.hashCode(createAlignmentCase) : 0);
        result = 31 * result + (billingAlignmentCase != null ? Arrays.hashCode(billingAlignmentCase) : 0);
        result = 31 * result + (priceListCase != null ? Arrays.hashCode(priceListCase) : 0);
        return result;
    }
}
