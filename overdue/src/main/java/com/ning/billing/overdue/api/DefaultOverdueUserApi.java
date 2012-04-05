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

package com.ning.billing.overdue.api;

import org.apache.commons.lang.NotImplementedException;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.StaticCatalog;
import com.ning.billing.catalog.api.overdue.BillingState;
import com.ning.billing.catalog.api.overdue.OverdueError;
import com.ning.billing.catalog.api.overdue.OverdueState;
import com.ning.billing.catalog.api.overdue.OverdueStateSet;
import com.ning.billing.catalog.api.overdue.Overdueable;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.overdue.OverdueService;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.util.overdue.dao.OverdueAccessDao;

public class DefaultOverdueUserApi implements OverdueUserApi{

    private OverdueService service;
    private CatalogService catalogService;
    private OverdueAccessDao accessDao;

    @Inject
    public DefaultOverdueUserApi(OverdueService service, CatalogService catalogService, OverdueAccessDao accessDao) {
        this.service = service;
        this.catalogService = catalogService;
        this.accessDao = accessDao;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Overdueable> OverdueState<T> getOverdueStateFor(T overdueable) throws OverdueError {
        try {
            String stateName = accessDao.getOverdueStateNameFor(overdueable);
            StaticCatalog catalog = catalogService.getCurrentCatalog();
            OverdueStateSet<SubscriptionBundle> states = catalog.currentBundleOverdueStateSet();
            return (OverdueState<T>) states.findState(stateName);
        } catch (CatalogApiException e) {
            throw new OverdueError(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED,overdueable.getId(), overdueable.getClass().getSimpleName());
        }
    }

    @Override
    public <T extends Overdueable> OverdueState<T> refreshOverdueStateFor(T overdueable) throws OverdueError {
        return service.refresh(overdueable);     
    } 

    @Override
    public <T extends Overdueable> void setOverrideBillingStateForAccount(
            T overdueable, BillingState<T> state) {
        throw new NotImplementedException();
    }
    
}
