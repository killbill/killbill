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

package com.ning.billing.overdue.listener;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.inject.Inject;

public class OverdueDispatcher {
    Logger log = LoggerFactory.getLogger(OverdueDispatcher.class);

    private final EntitlementInternalApi entitlementApi;
    private final OverdueWrapperFactory factory;

    @Inject
    public OverdueDispatcher(
            final EntitlementInternalApi entitlementApi,
            final OverdueWrapperFactory factory) {
        this.entitlementApi = entitlementApi;
        this.factory = factory;
    }

    public void processOverdueForAccount(final UUID accountId, final InternalCallContext context) {
        final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(accountId, context);
        for (final SubscriptionBundle bundle : bundles) {
            processOverdue(bundle, context);
        }
    }

    public void processOverdueForBundle(final UUID bundleId, final InternalCallContext context) {
        try {
            final SubscriptionBundle bundle = entitlementApi.getBundleFromId(bundleId, context);
            processOverdue(bundle, context);
        } catch (EntitlementUserApiException e) {
            log.error("Error processing Overdue for Bundle with id: " + bundleId.toString(), e);
        }
    }

    public void processOverdue(final Blockable blockable, final InternalCallContext context) {
        try {
            factory.createOverdueWrapperFor(blockable).refresh(context);
        } catch (OverdueException e) {
            log.error("Error processing Overdue for Blockable with id: " + blockable.getId().toString(), e);
        } catch (OverdueApiException e) {
            log.error("Error processing Overdue for Blockable with id: " + blockable.getId().toString(), e);
        }
    }

    public void processOverdue(final Blockable.Type type, final UUID blockableId, final InternalCallContext context) {
        try {
            factory.createOverdueWrapperFor(type, blockableId, context).refresh(context);
        } catch (OverdueException e) {
            log.error("Error processing Overdue for Blockable with id: " + blockableId.toString(), e);
        } catch (OverdueApiException e) {
            log.error("Error processing Overdue for Blockable with id: " + blockableId.toString(), e);
        }
    }

}
