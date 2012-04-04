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

package com.ning.billing.overdue.applicator;

import org.apache.commons.lang.NotImplementedException;
import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.overdue.OverdueState;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

public class OverdueStateApplicatorBundle implements OverdueStateApplicator<SubscriptionBundle>{    
    private EntitlementUserApi entitlementApi;

    @Inject
    public OverdueStateApplicatorBundle(EntitlementUserApi entitlementApi){
        this.entitlementApi = entitlementApi;   
    }

    @Override
    public void apply(SubscriptionBundle bundle,
            OverdueState<SubscriptionBundle> previousOverdueState,
            OverdueState<SubscriptionBundle> nextOverdueState, DateTime timeOfNextCheck) {
        
        if(previousOverdueState.getName().equals(nextOverdueState.getName())) {
            return; // nothing to do
        }
        
        cancelBundle(bundle, previousOverdueState, nextOverdueState);
        storeNewState(bundle, nextOverdueState);
  
        if(timeOfNextCheck != null && !nextOverdueState.isClearState()) {
            createFutureNotification(bundle, timeOfNextCheck);
        }

        if(nextOverdueState.isClearState()) {
            clear(bundle);
        }
        
        //If new state is clear state reset next events and override table
        throw new NotImplementedException();
    }

    private void cancelBundle(SubscriptionBundle bundle,
            OverdueState<SubscriptionBundle> previousOverdueState,
            OverdueState<SubscriptionBundle> nextOverdueState) {
        // TODO Auto-generated method stub
        
    }

    private void storeNewState(SubscriptionBundle bundle,
            OverdueState<SubscriptionBundle> nextOverdueState) {
        // TODO Auto-generated method stub
        
    }

    private void createFutureNotification(SubscriptionBundle bundle,
            DateTime timeOfNextCheck) {
        // TODO Auto-generated method stub
        
    }

    private void clear(SubscriptionBundle bundle) {
        // TODO Clear any future events plus overrides
        
    }


}
