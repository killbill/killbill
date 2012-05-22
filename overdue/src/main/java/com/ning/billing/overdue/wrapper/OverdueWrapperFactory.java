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

package com.ning.billing.overdue.wrapper;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.overdue.applicator.OverdueStateApplicator;
import com.ning.billing.overdue.calculator.BillingStateCalculatorBundle;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.config.api.OverdueError;
import com.ning.billing.overdue.service.ExtendedOverdueService;
import com.ning.billing.util.clock.Clock;

public class OverdueWrapperFactory {
    private static final Logger log =  LoggerFactory.getLogger(OverdueWrapperFactory.class);

    private final EntitlementUserApi entitlementApi;
    private final BillingStateCalculatorBundle billingStateCalcuatorBundle;
    private final OverdueStateApplicator<SubscriptionBundle> overdueStateApplicatorBundle;
    private final BlockingApi api;
    private final Clock clock;
    private  OverdueConfig overdueConfig;

    @Inject
    public OverdueWrapperFactory(BlockingApi api, Clock clock, 
            BillingStateCalculatorBundle billingStateCalcuatorBundle, 
            OverdueStateApplicator<SubscriptionBundle> overdueStateApplicatorBundle,
            EntitlementUserApi entitlementApi) {
        this.billingStateCalcuatorBundle = billingStateCalcuatorBundle;
        this.overdueStateApplicatorBundle = overdueStateApplicatorBundle;
        this.entitlementApi = entitlementApi;
        this.api = api;
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    public <T extends Blockable> OverdueWrapper<T> createOverdueWrapperFor(T bloackable) throws OverdueError {
        if (overdueConfig == null) {
            throw new OverdueError(ErrorCode.OVERDUE_NOT_CONFIGURED);
        }
        if(bloackable instanceof SubscriptionBundle) {
            return (OverdueWrapper<T>)new OverdueWrapper<SubscriptionBundle>((SubscriptionBundle)bloackable, api, overdueConfig.getBundleStateSet(), 
                    clock, billingStateCalcuatorBundle, overdueStateApplicatorBundle );
        } else {
            throw new OverdueError(ErrorCode.OVERDUE_TYPE_NOT_SUPPORTED, bloackable.getId(), bloackable.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Blockable> OverdueWrapper<T> createOverdueWrapperFor(UUID id) throws OverdueError {
        BlockingState state = api.getBlockingStateFor(id);

        try {
            switch (state.getType()) {
            case SUBSCRIPTION_BUNDLE : {
                SubscriptionBundle bundle = entitlementApi.getBundleFromId(id);
                return (OverdueWrapper<T>)new OverdueWrapper<SubscriptionBundle>(bundle, api, overdueConfig.getBundleStateSet(), 
                        clock, billingStateCalcuatorBundle, overdueStateApplicatorBundle );
            }
            default : {
                throw new OverdueError(ErrorCode.OVERDUE_TYPE_NOT_SUPPORTED, id, state.getType());
            }
                
            }  
        } catch (EntitlementUserApiException e) {
            throw new OverdueError(e);
        }
    }
    
    public void setOverdueConfig(OverdueConfig config) {
        overdueConfig = config;
    }

}
