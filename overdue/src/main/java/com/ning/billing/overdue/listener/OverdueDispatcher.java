/*
 * Copyright 2010-2013 Ning, Inc.
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

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;

import com.google.inject.Inject;

public class OverdueDispatcher {

    Logger log = LoggerFactory.getLogger(OverdueDispatcher.class);

    private final SubscriptionBaseInternalApi subscriptionApi;
    private final OverdueWrapperFactory factory;

    @Inject
    public OverdueDispatcher(final SubscriptionBaseInternalApi subscriptionApi,
                             final OverdueWrapperFactory factory) {
        this.subscriptionApi = subscriptionApi;
        this.factory = factory;
    }

    public void processOverdueForAccount(final UUID accountId, final InternalCallContext context) {
        processOverdue(accountId, context);
    }

    public void clearOverdueForAccount(final UUID accountId, final InternalCallContext context) {
        final List<SubscriptionBaseBundle> bundles = subscriptionApi.getBundlesForAccount(accountId, context);
        for (final SubscriptionBaseBundle bundle : bundles) {
            clearOverdue(bundle.getId(), context);
        }
    }

    public void processOverdue(final UUID blockableId, final InternalCallContext context) {
        try {
            factory.createOverdueWrapperFor(blockableId, context).refresh(context);
        } catch (BillingExceptionBase e) {
            log.error(String.format("Error processing Overdue for blockable %s", blockableId), e);
        }
    }

    public void clearOverdue(final UUID blockableId, final InternalCallContext context) {
        try {
            factory.createOverdueWrapperFor(blockableId, context).clear(context);
        } catch (BillingExceptionBase e) {
            log.error(String.format("Error processing Overdue for blockable %s (type %s)", blockableId), e);
        }
    }
}
