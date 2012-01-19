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

package com.ning.billing.beatrix.integration.inv_ent;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.eventbus.Subscribe;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.invoice.api.InvoiceCreationNotification;

public class TestBusHandler {

    protected static final Logger log = LoggerFactory.getLogger(TestBusHandler.class);

    private final List<NextEvent> nextExpectedEvent;

    private volatile boolean completed;

    public TestBusHandler() {
        nextExpectedEvent = new Stack<NextEvent>();
        this.completed = false;
    }

    public enum NextEvent {
        MIGRATE_ENTITLEMENT,
        CREATE,
        CHANGE,
        CANCEL,
        UNCANCEL,
        PAUSE,
        RESUME,
        PHASE,
        INVOICE
    }

    @Subscribe
    public void handleEntitlementEvents(SubscriptionTransition event) {
        log.info(String.format("Got subscription event %s", event.toString()));
        switch (event.getTransitionType()) {
        case MIGRATE_ENTITLEMENT:
            assertEqualsNicely(NextEvent.MIGRATE_ENTITLEMENT);
            notifyIfStackEmpty();
            break;
        case CREATE:
            assertEqualsNicely(NextEvent.CREATE);
            notifyIfStackEmpty();

            break;
        case CANCEL:
            assertEqualsNicely(NextEvent.CANCEL);
            notifyIfStackEmpty();

            break;
        case CHANGE:
            assertEqualsNicely(NextEvent.CHANGE);
            notifyIfStackEmpty();

            break;
        case PAUSE:
            assertEqualsNicely(NextEvent.PAUSE);
            notifyIfStackEmpty();

            break;
        case RESUME:
            assertEqualsNicely(NextEvent.RESUME);
            notifyIfStackEmpty();

            break;
        case UNCANCEL:
            assertEqualsNicely(NextEvent.UNCANCEL);
            notifyIfStackEmpty();
            break;
        case PHASE:
            assertEqualsNicely(NextEvent.PHASE);
            notifyIfStackEmpty();
            break;
        default:
            throw new RuntimeException("Unexpected event type " + event.getRequestedTransitionTime());
        }
    }

    @Subscribe
    public void handleInvoiceEvents(InvoiceCreationNotification event) {
        log.info(String.format("Got Invoice event %s", event.toString()));
        assertEqualsNicely(NextEvent.INVOICE);
        notifyIfStackEmpty();

    }

    public void reset() {
        nextExpectedEvent.clear();
        completed = true;
    }

    public void pushExpectedEvent(NextEvent next) {
        synchronized (this) {
            nextExpectedEvent.add(next);
            completed = false;
        }
    }

    public boolean isCompleted(long timeout) {
        synchronized (this) {
            try {
                wait(timeout);
            } catch (Exception ignore) {
            }
        }
        if (!completed) {
            log.debug("TestBusHandler did not complete in " + timeout + " ms");
        }
        return completed;
    }

    private void notifyIfStackEmpty() {
        log.debug("notifyIfStackEmpty ENTER");
        synchronized (this) {
            if (nextExpectedEvent.isEmpty()) {
                log.debug("notifyIfStackEmpty EMPTY");
                completed = true;
                notify();
            }
        }
        log.debug("notifyIfStackEmpty EXIT");
    }

    private void assertEqualsNicely(NextEvent expected) {

        boolean foundIt = false;
        Iterator<NextEvent> it = nextExpectedEvent.iterator();
        while (it.hasNext()) {
            NextEvent ev = it.next();
            if (ev == expected) {
                it.remove();
                foundIt = true;
                break;
            }
        }
        if (!foundIt) {
            Joiner joiner = Joiner.on(" ");
            System.err.println("Expected event " + expected + " got " + joiner.join(nextExpectedEvent));
            System.exit(1);
        }
    }
}
