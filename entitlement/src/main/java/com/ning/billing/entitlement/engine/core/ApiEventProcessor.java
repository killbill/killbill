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

package com.ning.billing.entitlement.engine.core;

import java.util.Collection;
import java.util.List;

import com.google.inject.Inject;
import com.ning.billing.config.IEntitlementConfig;
import com.ning.billing.entitlement.engine.dao.IEntitlementDao;
import com.ning.billing.entitlement.events.IEntitlementEvent;
import com.ning.billing.util.clock.IClock;

public class ApiEventProcessor extends ApiEventProcessorBase {

    @Inject
    public ApiEventProcessor(IClock clock, IEntitlementDao dao, IEntitlementConfig config) {
        super(clock, dao, config);
    }


    @Override
    protected boolean doProcessEvents(int sequenceId) {
        List<IEntitlementEvent> claimedEvents = dao.getEventsReady(apiProcessorId, sequenceId);
        if (claimedEvents.size() == 0) {
            return false;
        }
        log.debug(String.format("ApiEventProcessor got %d events", claimedEvents.size()));
        return doProcessEventsFromList(sequenceId, claimedEvents);
    }


    protected boolean doProcessEventsFromList(int sequenceId, Collection<IEntitlementEvent> claimedEvents) {
        long prev = nbProcessedEvents;
        for (IEntitlementEvent cur : claimedEvents) {
            log.debug(String.format("ApiEventProcessor seq = %d got event %s", sequenceId, cur.getId()));
            listener.processEventReady(cur);
            nbProcessedEvents++;
        }
        log.debug(String.format("ApiEventProcessor processed %d events", nbProcessedEvents - prev));
        //log.debug(String.format("ApiEventProcessor seq = %d processed events %s", sequenceId, claimedEvents.get(0).getId()));
        dao.clearEventsReady(apiProcessorId, claimedEvents);
        log.debug(String.format("ApiEventProcessor cleared events %d", nbProcessedEvents - prev));
        //log.debug(String.format("ApiEventProcessor seq = %d cleared events %s", sequenceId, claimedEvents.get(0).getId()));
        return true;

    }
}
