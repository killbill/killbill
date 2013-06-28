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

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.bus.BusPersistentEvent;
import com.ning.billing.bus.PersistentBus.EventBusException;
import com.ning.billing.bus.PersistentBusConfig;
import com.ning.billing.bus.dao.BusEventEntry;
import com.ning.billing.bus.dao.PersistentBusSqlDao;
import com.ning.billing.queue.PersistentQueueBase;
import com.ning.billing.util.Hostname;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.svcapi.account.AccountInternalApi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;

public class PersistentExternalBus extends PersistentQueueBase implements ExternalBus {

    private static final long DELTA_IN_PROCESSING_TIME_MS = 1000L * 60L * 5L; // 5 minutes
    private static final int MAX_BUS_EVENTS = 1;

    private static final Logger log = LoggerFactory.getLogger(PersistentExternalBus.class);

    private final PersistentBusSqlDao dao;

    private final EventBusDelegate eventBusDelegate;
    private final Clock clock;

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
        this.dao = dbi.onDemand(PersistentBusSqlDao.class);
        this.clock = clock;
        this.eventBusDelegate = new EventBusDelegate("Killbill EventBus");
    }

    @Override
    public int doProcessEvents() {

        final List<BusEventEntry> events = getNextBusEvent();
        if (events.size() == 0) {
            return 0;
        }

        int result = 0;
        for (final BusEventEntry cur : events) {
            final String jsonWithAccountAndTenantRecorId = tweakJsonToIncludeAccountAndTenantRecordId(cur.getBusEventJson(), cur.getAccountRecordId(), cur.getTenantRecordId());
            final BusPersistentEvent evt = deserializeEvent(cur.getBusEventClass(), objectMapper, jsonWithAccountAndTenantRecorId);

            //TODO STEPH needs to be fixed with accountId and tenantId...

            //final UUID accountId = getAccountIdFromRecordId(evt.getAccountRecordId());
            //final BusPersistentEvent evtWithAccountAndTenantId = new BusPersistentEvent(evt, );
            result++;
            // STEPH exception handling is done by GUAVA-- logged a bug Issue-780
            eventBusDelegate.post(evt);
            dao.clearBusEvent(cur.getId(), com.ning.billing.Hostname.get());
        }
        return result;
    }


    private List<BusEventEntry> getNextBusEvent() {

        final Date now = clock.getUTCNow().toDate();
        final Date nextAvailable = clock.getUTCNow().plus(DELTA_IN_PROCESSING_TIME_MS).toDate();

        final List<BusEventEntry> entries = dao.getNextBusEventEntries(config.getPrefetchAmount(), com.ning.billing.Hostname.get(), now);
        final List<BusEventEntry> claimedEntries = new LinkedList<BusEventEntry>();
        for (final BusEventEntry entry : entries) {
            final boolean claimed = (dao.claimBusEvent(com.ning.billing.Hostname.get(), nextAvailable, entry.getId(), now) == 1);
            if (claimed) {
                dao.insertClaimedHistory(com.ning.billing.Hostname.get(), now, entry.getId(), entry.getAccountRecordId(), entry.getTenantRecordId());
                claimedEntries.add(entry);
            }
        }
        return claimedEntries;
    }

    @Override
    public void register(final Object handlerInstance) /* throws EventBusException */ {
        eventBusDelegate.register(handlerInstance);
    }

    @Override
    public void unregister(final Object handlerInstance) /* throws EventBusException */ {
        eventBusDelegate.unregister(handlerInstance);
    }

    public void post(final BusPersistentEvent event, final InternalCallContext context) throws EventBusException {

        final String json;
        try {
            json = objectMapper.writeValueAsString(event);
            final BusEventEntry entry = new BusEventEntry(com.ning.billing.Hostname.get(), event.getClass().getName(), json, event.getUserToken(), event.getAccountRecordId(), event.getTenantRecordId());
            dao.insertBusEvent(entry);

        } catch (JsonProcessingException e) {
            throw new EventBusException("Failed to serialize ext bus event", e);
        }
    }

    private String tweakJsonToIncludeAccountAndTenantRecordId(final String input, final Long accountRecordId, final Long tenantRecordId) {
        final int lastIndexPriorFinalBracket = input.lastIndexOf("}");
        final StringBuilder tmp = new StringBuilder(input.substring(0, lastIndexPriorFinalBracket));
        tmp.append(",\"accountRecordId\":");
        tmp.append(accountRecordId);
        tmp.append(",\"tenantRecordId\":");
        tmp.append(tenantRecordId);
        tmp.append("}");
        return tmp.toString();
    }
}
