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

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.overdue.OverdueError;
import com.ning.billing.catalog.api.overdue.Overdueable;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.overdue.applicator.OverdueStateApplicatorBundle;
import com.ning.billing.overdue.calculator.BillingStateCalculatorBundle;
import com.ning.billing.overdue.dao.OverdueDao;
import com.ning.billing.util.clock.Clock;

public class OverdueWrapperFactory {

    private final CatalogService catalogService;
    private final BillingStateCalculatorBundle billingStateCalcuatorBundle;
    private final OverdueStateApplicatorBundle overdueStateApplicatorBundle;
    private final OverdueDao dao;
    private final Clock clock;

    @Inject
    public OverdueWrapperFactory(OverdueDao dao, CatalogService catalogService, Clock clock, BillingStateCalculatorBundle billingStateCalcuatorBundle, OverdueStateApplicatorBundle overdueStateApplicatorBundle) {
        this.billingStateCalcuatorBundle = billingStateCalcuatorBundle;
        this.overdueStateApplicatorBundle = overdueStateApplicatorBundle;
        this.catalogService = catalogService;
        this.dao = dao;
        this.clock = clock;
    }


    @SuppressWarnings("unchecked")
    public <T extends Overdueable> OverdueWrapper<T> createOverdueWrapperFor(T overdueable) throws OverdueError {
        try {
            if(overdueable instanceof SubscriptionBundle) {
                return (OverdueWrapper<T>)new OverdueWrapper<SubscriptionBundle>((SubscriptionBundle)overdueable, dao, catalogService.getCurrentCatalog().currentBundleOverdueStateSet(), 
                        clock, billingStateCalcuatorBundle, overdueStateApplicatorBundle );
            } else {
                throw new OverdueError(ErrorCode.OVERDUE_OVERDUEABLE_NOT_SUPPORTED, overdueable.getClass());
            }

        } catch (CatalogApiException e) {
            throw new OverdueError(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED, overdueable.getId().toString(), overdueable.getClass().toString());
        }
    }


}
