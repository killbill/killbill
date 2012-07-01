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
package com.ning.billing.jaxrs.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.common.eventbus.Subscribe;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.userrequest.CompletionUserRequest;
import com.ning.billing.util.userrequest.CompletionUserRequestNotifier;

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
    @Subscribe
    public void handleEntitlementevents(final BusEvent event) {
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
