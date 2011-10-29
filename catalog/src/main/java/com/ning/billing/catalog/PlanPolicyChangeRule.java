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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.PhaseType;

@XmlAccessorType(XmlAccessType.NONE)
public class PlanPolicyChangeRule extends ValidatingConfig {
	public enum Qualifier {
		DEFAULT,
		PRODUCT_FROM_LOW_TO_HIGH,
		PRODUCT_FROM_HIGH_TO_LOW,
		TERM_FROM_SHORT_TO_LONG,
		TERM_FROM_LONG_TO_SHORT
	}
	
	@XmlElement(required=false)
	private PhaseType restrictedToPhaseType;

	@XmlElement(required=true)
	private Qualifier qualifier;

	@XmlElement(required=true)
	private ActionPolicy policy;

	public PlanPolicyChangeRule(){}
	
	protected PlanPolicyChangeRule(Qualifier qualifier, ActionPolicy policy, PhaseType type) {
		this.qualifier = qualifier;
		this.policy = policy;
		this.restrictedToPhaseType = type;
	}
	
	public Qualifier getQualifier() {
		return qualifier;
	}

	public ActionPolicy getPolicy() {
		return policy;
	}
	
	public PhaseType getRestrictedToPhaseType() {
		return restrictedToPhaseType;
	}

	public ActionPolicy getPlanChangePolicy(
			int fromProductIndex, int fromBillingPeriodIndex,
			int toProductIndex,   int toBillingPeriodIndex, 
			PhaseType fromType) {
		if(restrictedToPhaseType != null ) {
			if(fromType != restrictedToPhaseType) {
				return null;
			}
		}
		if (qualifier == Qualifier.DEFAULT ) {
			return policy;
		} else if(qualifier == Qualifier.PRODUCT_FROM_HIGH_TO_LOW && fromProductIndex > toProductIndex) {
			return policy;
		} else if (qualifier == Qualifier.PRODUCT_FROM_LOW_TO_HIGH && fromProductIndex < toProductIndex) {
			return policy;
		} else if (qualifier == Qualifier.TERM_FROM_LONG_TO_SHORT && fromBillingPeriodIndex > toBillingPeriodIndex) {
			return policy;
		} else if (qualifier == Qualifier.TERM_FROM_SHORT_TO_LONG && fromBillingPeriodIndex < toBillingPeriodIndex) {
			return policy;
		}
		return null;	
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		// TODO Auto-generated method stub
		return errors;
	}


}
