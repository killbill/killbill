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

package com.ning.billing.overdue.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.apache.commons.lang.NotImplementedException;
import org.joda.time.DateTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.OverdueStateSet;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class DefaultOverdueStateSet<T extends Blockable> extends ValidatingConfig<OverdueConfig> implements OverdueStateSet<T> {
    private DefaultOverdueState<T> clearState;
    
    protected abstract DefaultOverdueState<T>[] getStates();
    
    private DefaultOverdueState<T> getClearState() throws CatalogApiException {
        for(DefaultOverdueState<T> overdueState : getStates()) {
            if(overdueState.isClearState()) {   
                return overdueState;
            }
        }
        throw new CatalogApiException(ErrorCode.CAT_MISSING_CLEAR_STATE);
    }
    
    @Override
    public OverdueState<T> findState(String stateName) throws CatalogApiException {
        for(DefaultOverdueState<T> state: getStates()) {
            if(state.getName().equals(stateName) ) { return state; }
        }
        throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_OVEDUE_STATE, stateName);
    }
    
    
    /* (non-Javadoc)
     * @see com.ning.billing.catalog.overdue.OverdueBillingState#findClearState()
     */
    @Override
    public DefaultOverdueState<T> findClearState() throws CatalogApiException {
        if (clearState != null) {
            clearState = getClearState();
        }
        return clearState;
    }
    
    /* (non-Javadoc)
     * @see com.ning.billing.catalog.overdue.OverdueBillingState#calculateOverdueState(com.ning.billing.catalog.api.overdue.BillingState, org.joda.time.DateTime)
     */
    @Override
    public DefaultOverdueState<T> calculateOverdueState(BillingState<T> billingState, DateTime now) throws CatalogApiException {         
            for(DefaultOverdueState<T> overdueState : getStates()) {
                if(overdueState.getCondition().evaluate(billingState, now)) {   
                    return overdueState;
                }
            }
            return  findClearState();
    }

    @Override
    public DateTime dateOfNextCheck(BillingState<T> billingState, DateTime now) {
        throw new NotImplementedException();
    }
        

    @Override
    public ValidationErrors validate(OverdueConfig root,
            ValidationErrors errors) {
        for(DefaultOverdueState<T> state: getStates()) {
            state.validate(root, errors);
        }
        try {
            getClearState();
        } catch (CatalogApiException e) {
            if(e.getCode() == ErrorCode.CAT_MISSING_CLEAR_STATE.getCode()) {
                errors.add("Overdue state set is missing a clear state.", 
                        root.getURI(), this.getClass(), "");
                }
        }
        
        return errors;
    }
}
