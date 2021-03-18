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

package org.killbill.billing.jaxrs.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.util.userrequest.CompletionUserRequest;
import org.killbill.billing.util.userrequest.CompletionUserRequestNotifier;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

public class KillbillEventHandler {

    private final List<CompletionUserRequest> activeWaiters;

    public KillbillEventHandler() {
        activeWaiters = new LinkedList<CompletionUserRequest>();
    }

    public void registerCompletionUserRequestWaiter(final CompletionUserRequest waiter) {
        if (waiter == null) {
            return;
        }
        synchronized (activeWaiters) {
            activeWaiters.add(waiter);
        }
    }

    public void unregisterCompletionUserRequestWaiter(final CompletionUserRequest waiter) {
        if (waiter == null) {
            return;
        }
        synchronized (activeWaiters) {
            activeWaiters.remove(waiter);
        }
    }

    /*
     * Killbill server event handler
     */
    @AllowConcurrentEvents
    @Subscribe
    public void handleSubscriptionEvents(final BusInternalEvent event) {
        // No BusDispatcherOptimizer logic on purpose
        final List<CompletionUserRequestNotifier> runningWaiters = new ArrayList<CompletionUserRequestNotifier>();
        synchronized (activeWaiters) {
            runningWaiters.addAll(activeWaiters);
        }
        if (runningWaiters.size() == 0) {
            return;
        }
        for (final CompletionUserRequestNotifier cur : runningWaiters) {
            cur.onBusEvent(event);
        }
    }
}
