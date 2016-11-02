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

package org.killbill.billing.overdue.listener;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;
import org.killbill.billing.callcontext.InternalCallContext;

import com.google.inject.Inject;

public class OverdueDispatcher {

    Logger log = LoggerFactory.getLogger(OverdueDispatcher.class);

    private final OverdueWrapperFactory factory;

    @Inject
    public OverdueDispatcher(final OverdueWrapperFactory factory) {
        this.factory = factory;
    }

    public void processOverdueForAccount(final UUID accountId, final DateTime effectiveDate, final InternalCallContext context) {
        processOverdue(accountId, effectiveDate, context);
    }

    public void clearOverdueForAccount(final UUID accountId, final DateTime effectiveDate, final InternalCallContext context) {
        clearOverdue(accountId, effectiveDate, context);
    }

    private void processOverdue(final UUID accountId, final DateTime effectiveDate, final InternalCallContext context) {
        try {
            factory.createOverdueWrapperFor(accountId, context).refresh(effectiveDate, context);
        } catch (BillingExceptionBase e) {
            log.warn("Error processing Overdue for accountId='{}'", accountId, e);
        }
    }

    private void clearOverdue(final UUID accountId, final DateTime effectiveDate, final InternalCallContext context) {
        try {
            factory.createOverdueWrapperFor(accountId, context).clear(effectiveDate, context);
        } catch (BillingExceptionBase e) {
            log.warn("Error processing Overdue for accountId='{}'", accountId, e);
        }
    }
}
