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

package com.ning.billing.api;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.InvoiceAdjustmentInternalEvent;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.events.PaymentErrorInternalEvent;
import com.ning.billing.util.events.PaymentInfoInternalEvent;
import com.ning.billing.util.events.RepairEntitlementInternalEvent;

import com.google.common.base.Joiner;
import com.google.common.eventbus.Subscribe;

public class TestApiListener {

    protected static final Logger log = LoggerFactory.getLogger(TestApiListener.class);

    private final List<NextEvent> nextExpectedEvent;

    private final TestListenerStatus testStatus;

    private boolean nonExpectedMode;

    private volatile boolean completed;

    @Inject
    public TestApiListener(final TestListenerStatus testStatus) {
        nextExpectedEvent = new Stack<NextEvent>();
        this.completed = false;
        this.testStatus = testStatus;
        this.nonExpectedMode = false;
    }

    public enum NextEvent {
        MIGRATE_ENTITLEMENT,
        MIGRATE_BILLING,
        CREATE,
        TRANSFER,
        RE_CREATE,
        CHANGE,
        CANCEL,
        UNCANCEL,
        PAUSE,
        RESUME,
        PHASE,
        INVOICE,
        INVOICE_ADJUSTMENT,
        PAYMENT,
        PAYMENT_ERROR,
        REPAIR_BUNDLE
    }

    public void setNonExpectedMode() {
        synchronized (this) {
            this.nonExpectedMode = true;
        }
    }

    @Subscribe
    public void handleEntitlementEvents(final RepairEntitlementInternalEvent event) {
        log.info(String.format("Got RepairEntitlementEvent event %s", event.toString()));
        assertEqualsNicely(NextEvent.REPAIR_BUNDLE);
        notifyIfStackEmpty();
    }

    @Subscribe
    public void handleEntitlementEvents(final EffectiveSubscriptionInternalEvent eventEffective) {
        log.info(String.format("Got subscription event %s", eventEffective.toString()));
        switch (eventEffective.getTransitionType()) {
        case TRANSFER:
            assertEqualsNicely(NextEvent.TRANSFER);
            notifyIfStackEmpty();
            break;
        case MIGRATE_ENTITLEMENT:
            assertEqualsNicely(NextEvent.MIGRATE_ENTITLEMENT);
            notifyIfStackEmpty();
            break;
            case MIGRATE_BILLING:
                assertEqualsNicely(NextEvent.MIGRATE_BILLING);
                notifyIfStackEmpty();
                break;
            case CREATE:
                assertEqualsNicely(NextEvent.CREATE);
                notifyIfStackEmpty();
                break;
            case RE_CREATE:
                assertEqualsNicely(NextEvent.RE_CREATE);
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
                throw new RuntimeException("Unexpected event type " + eventEffective.getRequestedTransitionTime());
        }
    }

    @Subscribe
    public void handleInvoiceEvents(final InvoiceCreationInternalEvent event) {
        log.info(String.format("Got Invoice event %s", event.toString()));
        assertEqualsNicely(NextEvent.INVOICE);
        notifyIfStackEmpty();
    }

    @Subscribe
    public void handleInvoiceAdjustmentEvents(final InvoiceAdjustmentInternalEvent event) {
        log.info(String.format("Got Invoice adjustment event %s", event.toString()));
        assertEqualsNicely(NextEvent.INVOICE_ADJUSTMENT);
        notifyIfStackEmpty();
    }

    @Subscribe
    public void handlePaymentEvents(final PaymentInfoInternalEvent event) {
        log.info(String.format("Got PaymentInfo event %s", event.toString()));
        assertEqualsNicely(NextEvent.PAYMENT);
        notifyIfStackEmpty();
    }

    @Subscribe
    public void handlePaymentErrorEvents(final PaymentErrorInternalEvent event) {
        log.info(String.format("Got PaymentError event %s", event.toString()));
        assertEqualsNicely(NextEvent.PAYMENT_ERROR);
        notifyIfStackEmpty();
    }

    public void reset() {
        synchronized (this) {
            nextExpectedEvent.clear();
            completed = true;
            nonExpectedMode = false;
        }
    }

    public void pushExpectedEvents(final NextEvent... events) {
        for (final NextEvent event : events) {
            pushExpectedEvent(event);
        }
    }

    public void pushExpectedEvent(final NextEvent next) {
        synchronized (this) {
            final Joiner joiner = Joiner.on(" ");
            nextExpectedEvent.add(next);
            log.info("Stacking expected event {}, got [{}]", next, joiner.join(nextExpectedEvent));
            completed = false;
        }
    }

    public boolean isCompleted(final long timeout) {
        synchronized (this) {
            if (completed) {
                return completed;
            }
            long waitTimeMs = timeout;
            do {
                try {
                    final DateTime before = new DateTime();
                    wait(500);
                    if (completed) {
                        return completed;
                    }
                    final DateTime after = new DateTime();
                    waitTimeMs -= after.getMillis() - before.getMillis();
                } catch (Exception ignore) {
                    log.error("isCompleted got interrupted ", ignore);
                    return false;
                }
            } while (waitTimeMs > 0 && !completed);
        }
        if (!completed && !nonExpectedMode) {
            final Joiner joiner = Joiner.on(" ");
            log.error("TestApiListener did not complete in " + timeout + " ms, remaining events are " + joiner.join(nextExpectedEvent));
        }
        return completed;
    }

    private void notifyIfStackEmpty() {
        log.debug("TestApiListener notifyIfStackEmpty ENTER");
        synchronized (this) {
            if (nextExpectedEvent.isEmpty()) {
                log.debug("notifyIfStackEmpty EMPTY");
                completed = true;
                notify();
            }
        }
        log.debug("TestApiListener notifyIfStackEmpty EXIT");
    }

    private void assertEqualsNicely(final NextEvent received) {

        synchronized (this) {
            boolean foundIt = false;
            final Iterator<NextEvent> it = nextExpectedEvent.iterator();
            while (it.hasNext()) {
                final NextEvent ev = it.next();
                if (ev == received) {
                    it.remove();
                    foundIt = true;
                    if (!nonExpectedMode) {
                        log.info("Found expected event {}. Yeah!", received);
                    } else {
                        log.error("Found non expected event {}. Boohh! ", received);
                    }
                    break;
                }
            }
            if (!foundIt && !nonExpectedMode) {
                final Joiner joiner = Joiner.on(" ");
                log.error("Received unexpected event " + received + "; remaining expected events [" + joiner.join(nextExpectedEvent) + "]");
                if (testStatus != null) {
                    testStatus.failed("TestApiListener [ApiListenerStatus]: Received unexpected event " + received + "; remaining expected events [" + joiner.join(nextExpectedEvent) + "]");
                }
            }
        }
    }
}
