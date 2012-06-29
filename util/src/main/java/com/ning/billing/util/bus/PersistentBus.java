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

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;
import com.ning.billing.util.Hostname;
import com.ning.billing.util.bus.dao.BusEventEntry;
import com.ning.billing.util.bus.dao.PersistentBusSqlDao;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.jackson.ObjectMapper;
import com.ning.billing.util.queue.PersistentQueueBase;

public class PersistentBus extends PersistentQueueBase implements Bus {

    private static final long DELTA_IN_PROCESSING_TIME_MS = 1000L * 60L * 5L; // 5 minutes
    private static final int MAX_BUS_EVENTS = 1;

    private static final Logger log = LoggerFactory.getLogger(PersistentBus.class);

    private final PersistentBusSqlDao dao;


    private final EventBusDelegate eventBusDelegate;
    private final Clock clock;
    private final String hostname;

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
    public PersistentBus(final IDBI dbi, final Clock clock, final PersistentBusConfig config) {

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
    }

    @Override
    public void start() {
        startQueue();
    }

    @Override
    public void stop() {
        stopQueue();
    }

    @Override
    public int doProcessEvents() {

        final List<BusEventEntry> events = getNextBusEvent();
        if (events.size() == 0) {
            return 0;
        }

        int result = 0;
        for (final BusEventEntry cur : events) {
            final BusEvent evt = deserializeEvent(cur.getBusEventClass(), cur.getBusEventJson());
            result++;
            // STEPH exception handling is done by GUAVA-- logged a bug Issue-780
            eventBusDelegate.post(evt);
            dao.clearBusEvent(cur.getId(), hostname);
        }
        return result;
    }


    private List<BusEventEntry> getNextBusEvent() {

        final Date now = clock.getUTCNow().toDate();
        final Date nextAvailable = clock.getUTCNow().plus(DELTA_IN_PROCESSING_TIME_MS).toDate();

        final BusEventEntry input = dao.getNextBusEventEntry(MAX_BUS_EVENTS, hostname, now);
        if (input == null) {
            return Collections.emptyList();
        }

        final boolean claimed = (dao.claimBusEvent(hostname, nextAvailable, input.getId(), now) == 1);
        if (claimed) {
            dao.insertClaimedHistory(hostname, now, input.getId());
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
    public void post(final BusEvent event) throws EventBusException {
        dao.inTransaction(new Transaction<Void, PersistentBusSqlDao>() {
            @Override
            public Void inTransaction(final PersistentBusSqlDao transactional,
                                      final TransactionStatus status) throws Exception {
                postFromTransaction(event, transactional);
                return null;
            }
        });
    }

    @Override
    public void postFromTransaction(final BusEvent event, final Transmogrifier transmogrifier)
            throws EventBusException {
        final PersistentBusSqlDao transactional = transmogrifier.become(PersistentBusSqlDao.class);
        postFromTransaction(event, transactional);
    }

    private void postFromTransaction(final BusEvent event, final PersistentBusSqlDao transactional) {
        try {
            final String json = objectMapper.writeValueAsString(event);
            final BusEventEntry entry = new BusEventEntry(hostname, event.getClass().getName(), json);
            transactional.insertBusEvent(entry);
        } catch (Exception e) {
            log.error("Failed to post BusEvent " + event, e);
        }
    }
}
