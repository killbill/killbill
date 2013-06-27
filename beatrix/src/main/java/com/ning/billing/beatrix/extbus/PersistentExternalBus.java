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

package com.ning.billing.beatrix.extbus;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.beatrix.extbus.dao.ExtBusEventEntry;
import com.ning.billing.beatrix.extbus.dao.ExtBusSqlDao;
import com.ning.billing.bus.PersistentBus.EventBusException;
import com.ning.billing.bus.PersistentBusConfig;
import com.ning.billing.notification.plugin.api.ExtBusEvent;
import com.ning.billing.notification.plugin.api.ExtBusEventType;
import com.ning.billing.queue.PersistentQueueBase;
import com.ning.billing.util.Hostname;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.svcapi.account.AccountInternalApi;

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;

public class PersistentExternalBus extends PersistentQueueBase implements ExternalBus {

    private static final long DELTA_IN_PROCESSING_TIME_MS = 1000L * 60L * 5L; // 5 minutes
    private static final int MAX_BUS_EVENTS = 1;

    private static final Logger log = LoggerFactory.getLogger(PersistentExternalBus.class);

    private final ExtBusSqlDao dao;

    private final EventBusDelegate eventBusDelegate;
    private final Clock clock;
    private final String hostname;
    private final InternalCallContextFactory internalCallContextFactory;
    private final AccountInternalApi accountApi;

    private static final class EventBusDelegate extends EventBus {

        public EventBusDelegate(final String busName) {
            super(busName);
        }
    }

    @Inject
    public PersistentExternalBus(final AccountInternalApi accountApi, final IDBI dbi, final Clock clock, final PersistentBusConfig config, final InternalCallContextFactory internalCallContextFactory) {
        super("Bus", Executors.newFixedThreadPool(config.getNbThreads(), new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new ThreadGroup(DefaultBusService.EVENT_BUS_GROUP_NAME),
                                  r,
                                  DefaultBusService.EVENT_BUS_TH_NAME);
            }
        }), config.getNbThreads(), config);
        this.dao = dbi.onDemand(ExtBusSqlDao.class);
        this.clock = clock;
        this.eventBusDelegate = new EventBusDelegate("Killbill EventBus");
        this.hostname = Hostname.get();
        this.internalCallContextFactory = internalCallContextFactory;
        this.accountApi = accountApi;
    }

    @Override
    public int doProcessEvents() {

        // TODO API_FIX Retrieving and clearing bus events is not done per tenant so pass default INTERNAL_TENANT_RECORD_ID; not sure this is something we want to do anyway ?
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID, null, "ExtPersistentBus", CallOrigin.INTERNAL, UserType.SYSTEM, null);
        final List<ExtBusEventEntry> events = getNextBusEvent(context);
        if (events.size() == 0) {
            return 0;
        }

        int result = 0;
        for (final ExtBusEventEntry cur : events) {
            // The accountRecordId for a newly created account is not set
            final UUID accountId;
            if (cur.getObjectType() == ObjectType.ACCOUNT && cur.getExtBusType() == ExtBusEventType.ACCOUNT_CREATION) {
                accountId = cur.getObjectId();
            } else {
                accountId = getAccountIdFromRecordId(cur.getAccountRecordId(), context);
            }
            final ExtBusEvent event = new DefaultBusEvent(cur.getExtBusType(), cur.getObjectType(), cur.getObjectId(), accountId, null);
            result++;
            // STEPH exception handling is done by GUAVA-- logged a bug Issue-780
            eventBusDelegate.post(event);
            final InternalCallContext rehydratedContext = internalCallContextFactory.createInternalCallContext(cur.getTenantRecordId(), cur.getAccountRecordId(), context);
            dao.clearBusExtEvent(cur.getId(), hostname, rehydratedContext);
        }
        return result;
    }

    private final UUID getAccountIdFromRecordId(final Long recordId, final InternalCallContext context) {
        try {
            final Account account = accountApi.getAccountByRecordId(recordId, context);
            return account.getId();
        } catch (final AccountApiException e) {
            log.warn("Failed to retrieve acount from recordId {}", recordId);
            return null;
        }
    }

    private List<ExtBusEventEntry> getNextBusEvent(final InternalCallContext context) {
        final Date now = clock.getUTCNow().toDate();
        final Date nextAvailable = clock.getUTCNow().plus(DELTA_IN_PROCESSING_TIME_MS).toDate();

        final ExtBusEventEntry input = dao.getNextBusExtEventEntry(MAX_BUS_EVENTS, hostname, now, context);
        if (input == null) {
            return Collections.emptyList();
        }

        // We need to re-hydrate the context with the record ids from the ExtBusEventEntry
        final InternalCallContext rehydratedContext = internalCallContextFactory.createInternalCallContext(input.getTenantRecordId(), input.getAccountRecordId(), context);
        final boolean claimed = (dao.claimBusExtEvent(hostname, nextAvailable, input.getId(), now, rehydratedContext) == 1);
        if (claimed) {
            dao.insertClaimedExtHistory(hostname, now, input.getId(), rehydratedContext);
            return Collections.singletonList(input);
        }
        return Collections.emptyList();
    }

    @Override
    public void register(final Object handlerInstance) /* throws EventBusException */ {
        eventBusDelegate.register(handlerInstance);
    }

    @Override
    public void unregister(final Object handlerInstance) /* throws EventBusException */ {
        eventBusDelegate.unregister(handlerInstance);
    }

    public void post(final ExtBusEventEntry event, final InternalCallContext context) throws EventBusException {
        dao.insertBusExtEvent(event, context);
    }
}
