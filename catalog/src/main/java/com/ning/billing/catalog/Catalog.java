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

import java.net.URL;
import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class Catalog extends ValidatingConfig implements ICatalog {
	public PlanRules getPlanRules() {
		return planRules;
	}

	public void setPlanRules(PlanRules planRules) {
		this.planRules = planRules;
	}

	@XmlElement(required=true)
	private Date effectiveDate;

	@XmlElement(required=true)
	private String catalogName;

	private URL catalogURL;

	@XmlElementWrapper(name="currencies", required=true)
	@XmlElement(name="currency", required=true)
	private Currency[] supportedCurrencies;

	@XmlElementWrapper(name="products", required=true)
	@XmlElement(name="product", required=true)
	private Product[] products;

	@XmlElement(name="rules", required=true)
    private PlanRules planRules;

	@XmlElementWrapper(name="plans", required=true)
	@XmlElement(name="plan", required=true)
	private Plan[] plans;

	@XmlElementWrapper(name="priceLists", required=true)
	@XmlElement(name="priceList", required=true)
	private PriceList[] priceLists;

	public Catalog() {}

	protected Catalog (Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	@Override
	public void initialize(Catalog catalog) {
		super.initialize(catalog);
		planRules.initialize(catalog);
		for(Product p : products) {
			p.initialize(catalog);
		}
		for(Plan p : plans) {
			p.initialize(catalog);
		}
		for(PriceList p : priceLists) {
			p.initialize(catalog);
		}

	}

    /* (non-Javadoc)
	 * @see com.ning.billing.catalog.ICatalog#getCalalogName()
	 */
    @Override
	public String getCalalogName() {
		return catalogName;
	}

    /* (non-Javadoc)
	 * @see com.ning.billing.catalog.ICatalog#getProducts()
	 */
    @Override
	public Product[] getProducts() {
		return products;
	}

	public void setProducts(Product[] products) {
		this.products = products;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.ICatalog#getPlanSets()
	 */
	@Override
	public PriceList[] getPriceLists() {
		return priceLists;
	}
 
	public void setPlanSets(PriceList[] planSets) {
		this.priceLists = planSets;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.ICatalog#getPriceListFromName(java.lang.String)
	 */
	@Override
	public PriceList getPriceListFromName(String priceListName) {
		for(PriceList set : priceLists) {
			if(set.getName().equals(priceListName)) {
				return set;
			}
		}
        return null;
    }

    /* (non-Javadoc)
	 * @see com.ning.billing.catalog.ICatalog#getPlan(java.lang.String, java.lang.String)
	 */
    @Override
	public Plan getPlan(String productName, BillingPeriod term, String planSetName) {

        PriceList planSet = getPriceListFromName(planSetName);
        if (planSet == null) {
            return null;
        }

        for (Plan cur : planSet.getPlans()) {
            if (cur.getProduct().getName().equals(productName) &&
                    cur.getBillingPeriod() == term) {
                return cur;
            }
        }
        return null;
    }

	@Override
	public Currency[] getSupportedCurrencies() {
		return supportedCurrencies;
	}

	public void setSupportedCurrencies(Currency[] supportedCurrencies) {
		this.supportedCurrencies = supportedCurrencies;
	}

	public void setPlanChangeRules(PlanRules planChangeRules) {
		this.planRules = planChangeRules;
	}

	@Override
	public Plan[] getPlans() {
		return plans;
	}

	public void setPlans(Plan[] plans) {
		this.plans = plans;
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		errors.addAll(validate(catalog,errors, products));
		errors.addAll(validate(catalog,errors, priceLists));
		errors.addAll(validate(catalog,errors, plans));
		errors.addAll(planRules.validate(catalog, errors));
		return errors;
	}

    @Override
    public ActionPolicy getPlanChangePolicy(PlanPhaseSpecifier from, PlanSpecifier to) {
        return planRules.getPlanChangePolicy(from, to, this);
    }
    
    @Override
    public PlanAlignmentChange getPlanChangeAlignment(PlanPhaseSpecifier from, PlanSpecifier to) {
        return planRules.getPlanChangeAlignment(from, to, this);
    }

    @Override
    public ActionPolicy getPlanCancelPolicy(PlanPhaseSpecifier planPhase) {
        return planRules.getPlanCancelPolicy(planPhase, this);
    }
    
    @Override
    public PlanAlignmentCreate getPlanCreateAlignment(PlanSpecifier planPhase) {
    	PlanPhaseSpecifier specifier = new PlanPhaseSpecifier(planPhase.getProductName(), planPhase.getProductCategory(), planPhase.getBillingPeriod(), planPhase.getPriceListName(), null);
    	
        return planRules.getPlanCreateAlignment(specifier, this);
    }
    
    @Override
    public BillingAlignment getBillingAlignment(PlanPhaseSpecifier planPhase) {
        return planRules.getBillingAlignment(planPhase, this);
    }


    @Override
    public Plan getPlanFromName(String name) {
        if (name == null) {
            return null;
        }
        for(Plan p : plans) {
        	if(p.getName().equals(name)) {
        		return p;
        	}
        }
        return null;
    }
    
    @Override
    public IProduct getProductFromName(String name) {
        for(Product p : products) {
        	if (p.getName().equals(name)) {
        		return p;
        	}
        }
        return null;
    }


    @Override
    public PlanPhase getPhaseFromName(String name) {

        if (name == null) {
            return null;
        }
        for(Plan p : plans) {

            if(p.getFinalPhase().getName().equals(name)) {
                return p.getFinalPhase();
            }
            if (p.getInitialPhases() != null) {
                for(PlanPhase pp : p.getInitialPhases()) {
                    if(pp.getName().equals(name)) {
                        return pp;
                    }
                }
            }
        }

        return null;
    }

    @Override
	public Date getEffectiveDate() {
		return effectiveDate;
	}

	public void setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public URL getCatalogURL() {
		return catalogURL;
	}

	public void setCatalogURL(URL catalogURL) {
		this.catalogURL = catalogURL;
	}

	@Override
    public PlanPhase getPhaseFor(String name, Date date) {
    	if(getEffectiveDate().getTime() >= date.getTime()){
    		return getPhaseFromName(name);
    	}
    	return null;
    }

	public void setPriceLists(PriceList[] priceLists) {
		this.priceLists = priceLists;
	}

	@Override
	public void configureEffectiveDate(Date date) {
		// Nothing to do here this is a method that is only inplemented on VersionedCatalog
		
	}
	
	//TODO: MDW validation - only allow one default pricelist

}
