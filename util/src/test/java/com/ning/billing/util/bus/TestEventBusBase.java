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

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.events.BusInternalEvent.BusInternalEventType;
import com.ning.billing.util.events.DefaultBusInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class TestEventBusBase {

    protected static final Logger log = LoggerFactory.getLogger(TestEventBusBase.class);

    private final InternalBus eventBus;
    private final InternalCallContext internalCallContext;

    public TestEventBusBase(final InternalBus eventBus, final InternalCallContext internalCallContext) {
        this.eventBus = eventBus;
        this.internalCallContext = internalCallContext;
    }

    public static class MyEvent extends DefaultBusInternalEvent implements BusInternalEvent {
        private final String name;
        private final Long value;
        private final String type;

        @JsonCreator
        public MyEvent(@JsonProperty("name") final String name,
                       @JsonProperty("value") final Long value,
                       @JsonProperty("token") final UUID token,
                       @JsonProperty("type") final String type,
                       @JsonProperty("accountRecordId") final Long accountRecordId,
                       @JsonProperty("tenantRecordId") final Long tenantRecordId) {
            super(token, accountRecordId, tenantRecordId);
            this.name = name;
            this.value = value;
            this.type = type;
        }

        @JsonIgnore
        @Override
        public BusInternalEventType getBusEventType() {
            return BusInternalEventType.valueOf(type);
        }

        public String getName() {
            return name;
        }

        public Long getValue() {
            return value;
        }

        public String getType() {
            return type;
        }
    }

    public static final class MyEventWithException extends MyEvent {

        @JsonCreator
        public MyEventWithException(@JsonProperty("name") final String name,
                                    @JsonProperty("value") final Long value,
                                    @JsonProperty("token") final UUID token,
                                    @JsonProperty("type") final String type,
                                    @JsonProperty("accountRecordId") final Long accountRecordId,
                                    @JsonProperty("tenantRecordId") final Long tenantRecordId) {
            super(name, value, token, type, accountRecordId, tenantRecordId);
        }
    }

    public static final class MyOtherEvent extends DefaultBusInternalEvent implements BusInternalEvent {

        private final String name;
        private final Double value;
        private final String type;

        @JsonCreator
        public MyOtherEvent(@JsonProperty("name") final String name,
                            @JsonProperty("value") final Double value,
                            @JsonProperty("token") final UUID token,
                            @JsonProperty("type") final String type,
                            @JsonProperty("accountRecordId") final Long accountRecordId,
                            @JsonProperty("tenantRecordId") final Long tenantRecordId) {
            super(token, accountRecordId, tenantRecordId);
            this.name = name;
            this.value = value;
            this.type = type;
        }

        @JsonIgnore
        @Override
        public BusInternalEventType getBusEventType() {
            return BusInternalEventType.valueOf(type);
        }


        public String getName() {
            return name;
        }

        public Double getValue() {
            return value;
        }

        public String getType() {
            return type;
        }
    }

    public static class MyEventHandlerException extends RuntimeException {

        private static final long serialVersionUID = 156337823L;

        public MyEventHandlerException(final String msg) {
            super(msg);
        }
    }

    public static class MyEventHandler {

        private final int expectedEvents;

        private volatile int gotEvents;

        public MyEventHandler(final int exp) {
            this.expectedEvents = exp;
            this.gotEvents = 0;
        }

        public synchronized int getEvents() {
            return gotEvents;
        }

        @Subscribe
        public synchronized void processEvent(final MyEvent event) {
            gotEvents++;
            //log.debug("Got event {} {}", event.name, event.value);
        }

        @Subscribe
        public synchronized void processEvent(final MyEventWithException event) {
            throw new MyEventHandlerException("FAIL");
        }

        public synchronized boolean waitForCompletion(final long timeoutMs) {

            final long ini = System.currentTimeMillis();
            long remaining = timeoutMs;
            while (gotEvents < expectedEvents && remaining > 0) {
                try {
                    wait(1000);
                    if (gotEvents == expectedEvents) {
                        break;
                    }
                    remaining = timeoutMs - (System.currentTimeMillis() - ini);
                } catch (InterruptedException ignore) {
                }
            }
            return (gotEvents == expectedEvents);
        }
    }

    public void testSimpleWithException() {
        try {
            final MyEventHandler handler = new MyEventHandler(1);
            eventBus.register(handler);

            eventBus.post(new MyEventWithException("my-event", 1L, UUID.randomUUID(), BusInternalEventType.ACCOUNT_CHANGE.toString(), 1L, 1L), internalCallContext);

            Thread.sleep(50000);
        } catch (Exception ignored) {
        }
    }

    public void testSimple() {
        try {
            final int nbEvents = 5;
            final MyEventHandler handler = new MyEventHandler(nbEvents);
            eventBus.register(handler);

            for (int i = 0; i < nbEvents; i++) {
                eventBus.post(new MyEvent("my-event", (long) i, UUID.randomUUID(), BusInternalEventType.ACCOUNT_CHANGE.toString(), 1L, 1L), internalCallContext);
            }

            final boolean completed = handler.waitForCompletion(10000);
            Assert.assertEquals(completed, true);
        } catch (Exception e) {
            Assert.fail("", e);
        }
    }

    public void testDifferentType() {
        try {
            final MyEventHandler handler = new MyEventHandler(1);
            eventBus.register(handler);

            for (int i = 0; i < 5; i++) {
                eventBus.post(new MyOtherEvent("my-other-event", (double) i, UUID.randomUUID(), BusInternalEventType.BUNDLE_REPAIR.toString(), 1L, 1L), internalCallContext);
            }
            eventBus.post(new MyEvent("my-event", 11l, UUID.randomUUID(), BusInternalEventType.ACCOUNT_CHANGE.toString(), 1L, 1L), internalCallContext);

            final boolean completed = handler.waitForCompletion(10000);
            Assert.assertEquals(completed, true);
        } catch (Exception e) {
            Assert.fail("", e);
        }
    }
}
