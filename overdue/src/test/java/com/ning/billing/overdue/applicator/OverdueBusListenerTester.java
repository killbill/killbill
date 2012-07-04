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

package com.ning.billing.overdue.applicator;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.ning.billing.overdue.OverdueChangeEvent;

public class OverdueBusListenerTester {
    public static final Logger log = LoggerFactory.getLogger(OverdueBusListenerTester.class);
    
    private List<OverdueChangeEvent> eventsReceived = new ArrayList<OverdueChangeEvent>();
    
    @Subscribe
    public void handleOverdueChange(final OverdueChangeEvent changeEvent) {
        log.info("Received subscription transition.");
        eventsReceived.add(changeEvent);
    }
    
    public List<OverdueChangeEvent> getEventsReceived() {
        return eventsReceived;
    }
    
    public void clearEventsReceived() {
        eventsReceived.clear();
    }

}
