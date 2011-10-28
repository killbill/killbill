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

package com.ning.billing.entitlement.api;

import java.util.EmptyStackException;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.entitlement.api.user.IApiListener;
import com.ning.billing.entitlement.api.user.ISubscriptionTransition;

public class ApiTestListener implements IApiListener {

    private static final Logger log = LoggerFactory.getLogger(ApiTestListener.class);

    private final Stack<NextEvent> nextExpectedEvent;

    private volatile boolean completed;

    public enum NextEvent {
        CREATE,
        CHANGE,
        CANCEL,
        PAUSE,
        RESUME,
        PHASE
    }

    public ApiTestListener() {
        this.nextExpectedEvent = new Stack<NextEvent>();
        this.completed = false;
    }

    public void pushExpectedEvent(NextEvent next) {
        synchronized (this) {
            nextExpectedEvent.push(next);
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
            log.warn("ApiTestListener did not complete in " + timeout + " ms");
        }
        return completed;
    }

    public void reset() {
        nextExpectedEvent.clear();
    }

    private void notifyIfStackEmpty() {
        log.info("notifyIfStackEmpty ENTER");
        synchronized (this) {
            if (nextExpectedEvent.isEmpty()) {
                log.info("notifyIfStackEmpty EMPTY");
                completed = true;
                notify();
            }
        }
        log.info("notifyIfStackEmpty EXIT");
    }

    private void assertEqualsNicely(NextEvent expected, NextEvent real) {
        if (expected != real) {
            System.err.println("Expected event " + expected + " got " + real);
            try {
                NextEvent next = nextExpectedEvent.pop();
                while (next != null) {
                    System.err.println("Also got event " + next);
                    next = nextExpectedEvent.pop();
                }
            } catch (EmptyStackException ignore) {
            }
            System.exit(1);
        }
    }

    @Override
    public void subscriptionCreated(ISubscriptionTransition created) {
        log.info("-> Got event CREATED");
        assertEqualsNicely(nextExpectedEvent.pop(), NextEvent.CREATE);
        notifyIfStackEmpty();
    }

    @Override
    public void subscriptionCancelled(ISubscriptionTransition cancelled) {
        log.info("-> Got event CANCEL");
        assertEqualsNicely(nextExpectedEvent.pop(), NextEvent.CANCEL);
        notifyIfStackEmpty();
    }

    @Override
    public void subscriptionChanged(ISubscriptionTransition changed) {
        log.info("-> Got event CHANGE");
        assertEqualsNicely(nextExpectedEvent.pop(), NextEvent.CHANGE);
        notifyIfStackEmpty();
    }

    @Override
    public void subscriptionPaused(ISubscriptionTransition paused) {
        log.info("-> Got event PAUSE");
        assertEqualsNicely(nextExpectedEvent.pop(), NextEvent.PAUSE);
        notifyIfStackEmpty();
    }

    @Override
    public void subscriptionResumed(ISubscriptionTransition resumed) {
        log.info("-> Got event RESUME");
        assertEqualsNicely(nextExpectedEvent.pop(), NextEvent.RESUME);
        notifyIfStackEmpty();
    }

    @Override
    public void subscriptionPhaseChanged(
            ISubscriptionTransition phaseChanged) {
        log.info("-> Got event PHASE");
        assertEqualsNicely(nextExpectedEvent.pop(), NextEvent.PHASE);
        notifyIfStackEmpty();
    }
}
