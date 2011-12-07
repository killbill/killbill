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

package com.ning.billing.catalog.rules;

import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.util.config.ValidationErrors;

import javax.xml.bind.annotation.XmlElement;

public abstract class CasePhase<T> extends CaseStandardNaming<T> {

	@XmlElement(required=false)
	private PhaseType phaseType;	
	
	public T getResult(PlanPhaseSpecifier specifier, StandaloneCatalog c) throws CatalogApiException {
		if (	
				(phaseType       == null || specifier.getPhaseType() == null || specifier.getPhaseType() == phaseType) &&
				satisfiesCase(new PlanSpecifier(specifier), c)
				) {
			return getResult(); 
		}
		return null;
	}
	
	public static <K> K getResult(CasePhase<K>[] cases, PlanPhaseSpecifier planSpec, StandaloneCatalog catalog) throws CatalogApiException {
    	if(cases != null) {
    		for(CasePhase<K> cp : cases) {
    			K result = cp.getResult(planSpec, catalog);
    			if(result != null) { 
    				return result; 
    			}        					
    		}
    	}
        return null;
        
    }

	@Override
	public ValidationErrors validate(StandaloneCatalog catalog, ValidationErrors errors) {
		return errors;
	}

	protected CasePhase<T> setPhaseType(PhaseType phaseType) {
		this.phaseType = phaseType;
		return this;
	}
	
	
}
