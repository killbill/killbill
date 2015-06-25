/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.catalog.rules;

import java.util.Arrays;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

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
        final PlanAlignmentCreate result =  DefaultCase.getResult(createAlignmentCase, specifier, catalog);
        return (result != null) ? result : PlanAlignmentCreate.START_OF_BUNDLE;
    }

    public BillingActionPolicy getPlanCancelPolicy(final PlanPhaseSpecifier planPhase, final StaticCatalog catalog) throws CatalogApiException {
        final BillingActionPolicy result =  DefaultCasePhase.getResult(cancelCase, planPhase, catalog);
        return (result != null) ? result : BillingActionPolicy.END_OF_TERM;
    }

    public BillingAlignment getBillingAlignment(final PlanPhaseSpecifier planPhase, final StaticCatalog catalog) throws CatalogApiException {
        final BillingAlignment result = DefaultCasePhase.getResult(billingAlignmentCase, planPhase, catalog);
        return (result != null) ?  result : BillingAlignment.ACCOUNT;
    }

    public PlanChangeResult planChange(final PlanPhaseSpecifier from, PlanSpecifier to, final StaticCatalog catalog) throws CatalogApiException {
        final DefaultPriceList toPriceList;
        if (to.getPriceListName() == null) { // Pricelist may be null because it is unspecified this is the principal use-case
            toPriceList = findPriceList(from.toPlanSpecifier(), catalog);
            to = new PlanSpecifier(to.getProductName(), to.getProductCategory(), to.getBillingPeriod(), toPriceList.getName());
        } else {
            toPriceList = (DefaultPriceList) catalog.findCurrentPricelist(to.getPriceListName());
        }

        final BillingActionPolicy policy = getPlanChangePolicy(from, to, catalog);
        if (policy == BillingActionPolicy.ILLEGAL) {
            throw new IllegalPlanChange(from, to);
        }

        final PlanAlignmentChange alignment = getPlanChangeAlignment(from, to, catalog);

        return new PlanChangeResult(toPriceList, policy, alignment);
    }

    public PlanAlignmentChange getPlanChangeAlignment(final PlanPhaseSpecifier from,
                                                      final PlanSpecifier to, final StaticCatalog catalog) throws CatalogApiException {
        final PlanAlignmentChange result =  DefaultCaseChange.getResult(changeAlignmentCase, from, to, catalog);
        return (result != null) ? result : PlanAlignmentChange.START_OF_BUNDLE;
    }

    public BillingActionPolicy getPlanChangePolicy(final PlanPhaseSpecifier from,
                                                   final PlanSpecifier to, final StaticCatalog catalog) throws CatalogApiException {
        if (from.getProductName().equals(to.getProductName()) &&
            from.getBillingPeriod() == to.getBillingPeriod() &&
            from.getPriceListName().equals(to.getPriceListName())) {
            return BillingActionPolicy.ILLEGAL;
        }
        //Plan toPlan = catalog.findPlan()

        final BillingActionPolicy result =  DefaultCaseChange.getResult(changeCase, from, to, catalog);
        return (result != null) ? result : BillingActionPolicy.END_OF_TERM;
    }

    private DefaultPriceList findPriceList(final PlanSpecifier specifier, final StaticCatalog catalog) throws CatalogApiException {
        DefaultPriceList result = DefaultCase.getResult(priceListCase, specifier, catalog);
        if (result == null) {
            result = (DefaultPriceList) catalog.findCurrentPricelist(specifier.getPriceListName());
        }
        return result;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        //TODO: MDW - Validation: check that the plan change special case pairs are unique!
        //TODO: MDW - Validation: check that the each product appears in at most one tier.
        //TODO: MDW - Unit tests for rules
        //TODO: MDW - validate that there is a default policy for change AND cancel

        return errors;
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
