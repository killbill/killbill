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
import com.ning.billing.ErrorCode;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.overdue.OverdueService;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.config.api.OverdueError;

public class OverdueStateApplicator<T extends Blockable>{

    private final BlockingApi blockingApi;


    @Inject
    public OverdueStateApplicator(BlockingApi accessApi) {
        this.blockingApi = accessApi;
    }

    public void apply(T overdueable, OverdueState<T> previousOverdueState, OverdueState<T> nextOverdueState, DateTime timeOfNextCheck) throws OverdueError {
        if(previousOverdueState.getName().equals(nextOverdueState.getName())) {
            return; // nothing to do
        }
        
        storeNewState(overdueable, nextOverdueState);
  
        if(timeOfNextCheck != null && !nextOverdueState.isClearState()) {
            createFutureNotification(overdueable, timeOfNextCheck);
        }

        if(nextOverdueState.isClearState()) {
            clear(overdueable);
        }
        
        //If new state is clear state reset next events and override table
        throw new NotImplementedException();
    }


    protected void storeNewState(T blockable, OverdueState<T> nextOverdueState) throws OverdueError {
        try {
            blockingApi.setBlockingState(new BlockingState(blockable.getId(), nextOverdueState.getName(), Blockable.Type.get(blockable), 
                    OverdueService.OVERDUE_SERVICE_NAME, blockChanges(nextOverdueState), blockEntitlement(nextOverdueState), blockBilling(nextOverdueState)));
        } catch (Exception e) {
            throw new OverdueError(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED, blockable.getId(), blockable.getClass().getName());
        }
    }

    private boolean blockChanges(OverdueState<T> nextOverdueState) {
        return nextOverdueState.blockChanges();
    }

    private boolean blockBilling(OverdueState<T> nextOverdueState) {
        return nextOverdueState.disableEntitlementAndChangesBlocked();
    }

    private boolean blockEntitlement(OverdueState<T> nextOverdueState) {
        return nextOverdueState.disableEntitlementAndChangesBlocked();
    }

    protected void createFutureNotification(T overdueable,
            DateTime timeOfNextCheck) {
        // TODO MDW
        
    }


    
    protected void clear(T overdueable) {
        //TODO MDW
        // Clear future notification checks
        // Clear any overrides
        
    }

}
