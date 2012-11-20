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

package com.ning.billing.util.bus;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.Hostname;
import com.ning.billing.util.bus.dao.BusEventEntry;
import com.ning.billing.util.bus.dao.PersistentBusSqlDao;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.queue.PersistentQueueBase;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;

public class PersistentInternalBus extends PersistentQueueBase implements InternalBus {

    private static final long DELTA_IN_PROCESSING_TIME_MS = 1000L * 60L * 5L; // 5 minutes
    private static final int MAX_BUS_EVENTS = 1;

    private static final Logger log = LoggerFactory.getLogger(PersistentInternalBus.class);

    private final PersistentBusSqlDao dao;

    private final EventBusDelegate eventBusDelegate;
    private final Clock clock;
    private final String hostname;
    private final InternalCallContextFactory internalCallContextFactory;

    private volatile boolean isStarted;

    private static final class EventBusDelegate extends EventBus {

        public EventBusDelegate(final String busName) {
            super(busName);
        }

        // STEPH we can't override the method because EventHandler is package private scope
        // Logged a bug against guava (Issue 981)
        /*
        @Override
        protected void dispatch(Object event, EventHandler wrapper) {
            try {
              wrapper.handleEvent(event);
            } catch (InvocationTargetException e) {
              logger.log(Level.SEVERE,
                  "Could not dispatch event: " + event + " to handler " + wrapper, e);
            }
          }
          */
    }

    @Inject
    public PersistentInternalBus(final IDBI dbi, final Clock clock, final PersistentBusConfig config, final InternalCallContextFactory internalCallContextFactory) {
        super("Bus", Executors.newFixedThreadPool(config.getNbThreads(), new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new ThreadGroup(DefaultBusService.EVENT_BUS_GROUP_NAME),
                                  r,
                                  DefaultBusService.EVENT_BUS_TH_NAME);
            }
        }), config.getNbThreads(), config);
        this.dao = dbi.onDemand(PersistentBusSqlDao.class);
        this.clock = clock;
        this.eventBusDelegate = new EventBusDelegate("Killbill EventBus");
        this.hostname = Hostname.get();
        this.internalCallContextFactory = internalCallContextFactory;
        this.isStarted = false;
    }

    @Override
    public void start() {
        startQueue();
        isStarted = true;
    }

    @Override
    public void stop() {
        stopQueue();
        isStarted = false;
    }

    @Override
    public int doProcessEvents() {

        // TODO API_FIX Retrieving and clearing bus events is not done per tenant so pass default INTERNAL_TENANT_RECORD_ID; not sure this is something we want to do anyway ?
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID, null, "PersistentBus", CallOrigin.INTERNAL, UserType.SYSTEM, null);
        final List<BusEventEntry> events = getNextBusEvent(context);
        if (events.size() == 0) {
            return 0;
        }

        int result = 0;
        for (final BusEventEntry cur : events) {
            final String jsonWithAccountAndTenantRecorId = tweakJsonToIncludeAccountAndTenantRecordId(cur.getBusEventJson(), cur.getAccountRecordId(), cur.getTenantRecordId());
            final BusInternalEvent evt = deserializeEvent(cur.getBusEventClass(), jsonWithAccountAndTenantRecorId);
            result++;
            // STEPH exception handling is done by GUAVA-- logged a bug Issue-780
            eventBusDelegate.post(evt);
            final InternalCallContext rehydratedContext = internalCallContextFactory.createInternalCallContext(cur.getTenantRecordId(), cur.getAccountRecordId(), context);
            dao.clearBusEvent(cur.getId(), hostname, rehydratedContext);
        }
        return result;
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    private List<BusEventEntry> getNextBusEvent(final InternalCallContext context) {
        final Date now = clock.getUTCNow().toDate();
        final Date nextAvailable = clock.getUTCNow().plus(DELTA_IN_PROCESSING_TIME_MS).toDate();

        final BusEventEntry input = dao.getNextBusEventEntry(MAX_BUS_EVENTS, hostname, now, context);
        if (input == null) {
            return Collections.emptyList();
        }

        // We need to re-hydrate the context with the record ids from the BusEventEntry
        final InternalCallContext rehydratedContext = internalCallContextFactory.createInternalCallContext(input.getTenantRecordId(), input.getAccountRecordId(), context);
        final boolean claimed = (dao.claimBusEvent(hostname, nextAvailable, input.getId(), now, rehydratedContext) == 1);
        if (claimed) {
            dao.insertClaimedHistory(hostname, now, input.getId(), rehydratedContext);
            return Collections.singletonList(input);
        }
        return Collections.emptyList();
    }

    @Override
    public void register(final Object handlerInstance) throws EventBusException {
        eventBusDelegate.register(handlerInstance);
    }

    @Override
    public void unregister(final Object handlerInstance) throws EventBusException {
        eventBusDelegate.unregister(handlerInstance);
    }

    @Override
    public void post(final BusInternalEvent event, final InternalCallContext context) throws EventBusException {
        dao.inTransaction(new Transaction<Void, PersistentBusSqlDao>() {
            @Override
            public Void inTransaction(final PersistentBusSqlDao transactional,
                                      final TransactionStatus status) throws Exception {
                postFromTransaction(event, context, transactional);
                return null;
            }
        });
    }

    @Override
    public void postFromTransaction(final BusInternalEvent event, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context)
            throws EventBusException {
        final PersistentBusSqlDao transactional = entitySqlDaoWrapperFactory.transmogrify(PersistentBusSqlDao.class);
        postFromTransaction(event, context, transactional);
    }

    private void postFromTransaction(final BusInternalEvent event, final InternalCallContext context, final PersistentBusSqlDao transactional) {
        try {
            final String json = objectMapper.writeValueAsString(event);
            final BusEventEntry entry = new BusEventEntry(hostname, event.getClass().getName(), json, context.getAccountRecordId(), context.getTenantRecordId());
            transactional.insertBusEvent(entry, context);
        } catch (Exception e) {
            log.error("Failed to post BusEvent " + event, e);
        }
    }


    private String tweakJsonToIncludeAccountAndTenantRecordId(final String input, final Long accountRecordId, final Long tenantRecordId) {
        int lastIndexPriorFinalBracket = input.lastIndexOf("}");
        final StringBuilder tmp = new StringBuilder(input.substring(0, lastIndexPriorFinalBracket));
        tmp.append(",\"accountRecordId\":");
        tmp.append(accountRecordId);
        tmp.append(",\"tenantRecordId\":");
        tmp.append(tenantRecordId);
        tmp.append("}");
        return tmp.toString();
    }

}
