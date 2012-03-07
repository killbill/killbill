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

package com.ning.billing.beatrix.integration;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.google.common.base.Joiner;
import com.google.common.eventbus.Subscribe;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;

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
        INVOICE,
        PAYMENT
    }

    @Subscribe
    public void handleEntitlementEvents(SubscriptionTransition event) {
        log.info(String.format("TestBusHandler Got subscription event %s", event.toString()));
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
        log.info(String.format("TestBusHandler Got Invoice event %s", event.toString()));
        assertEqualsNicely(NextEvent.INVOICE);
        notifyIfStackEmpty();

    }

    @Subscribe
    public void handlePaymentEvents(PaymentInfo event) {
        log.info(String.format("TestBusHandler Got PaymentInfo event %s", event.toString()));
        assertEqualsNicely(NextEvent.PAYMENT);
        notifyIfStackEmpty();
    }

    @Subscribe
    public void handlePaymentErrorEvents(PaymentError event) {
        log.info(String.format("TestBusHandler Got PaymentError event %s", event.toString()));
        //Assert.fail("Unexpected payment failure");
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
            if (completed) {
                return completed;
            }
            try {
                wait(timeout);
            } catch (Exception ignore) {
            }
        }
        if (!completed) {
            Joiner joiner = Joiner.on(" ");
            log.error("TestBusHandler did not complete in " + timeout + " ms, remaining events are " + joiner.join(nextExpectedEvent));
        }
        return completed;
    }

    private void notifyIfStackEmpty() {
        log.debug("TestBusHandler notifyIfStackEmpty ENTER");
        synchronized (this) {
            if (nextExpectedEvent.isEmpty()) {
                log.debug("notifyIfStackEmpty EMPTY");
                completed = true;
                notify();
            }
        }
        log.debug("TestBusHandler notifyIfStackEmpty EXIT");
    }

    private void assertEqualsNicely(NextEvent received) {

        synchronized(this) {
            boolean foundIt = false;
            Iterator<NextEvent> it = nextExpectedEvent.iterator();
            while (it.hasNext()) {
                NextEvent ev = it.next();
                if (ev == received) {
                    it.remove();
                    foundIt = true;
                    break;
                }
            }
            if (!foundIt) {
                Joiner joiner = Joiner.on(" ");
                log.error("TestBusHandler Received event " + received + "; expected " + joiner.join(nextExpectedEvent));
                Assert.fail();
            }
        }
    }
}
