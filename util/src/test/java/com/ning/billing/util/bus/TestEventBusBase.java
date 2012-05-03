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

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.util.bus.BusEvent.BusEventType;


public class TestEventBusBase {

    protected static final Logger log = LoggerFactory.getLogger(TestEventBusBase.class);

    @Inject
    protected Bus eventBus;

    @BeforeClass(groups = "slow")
    public void setup() throws Exception {
        eventBus.start();
    }
    
    @AfterClass(groups = "slow")
    public void tearDown() {
        eventBus.stop();
    }

    
    public static  class MyEvent implements BusEvent {
        
        private String name;
        private Long value;
        private UUID userToken;
        private String type;

        @JsonCreator
        public MyEvent(@JsonProperty("name") String name,
                @JsonProperty("value") Long value,
                @JsonProperty("token") UUID token,
                @JsonProperty("type") String type) {
                
            this.name = name;
            this.value = value;
            this.userToken = token;
            this.type = type;
        }

        @JsonIgnore
        @Override
        public BusEventType getBusEventType() {
            return BusEventType.valueOf(type);
        }

        @Override
        public UUID getUserToken() {
            return userToken;
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
        public MyEventWithException(@JsonProperty("name") String name,
                @JsonProperty("value") Long value,
                @JsonProperty("token") UUID token,
                @JsonProperty("type") String type) {
            super(name, value, token, type);
        }        
    }


    public static final class MyOtherEvent implements BusEvent {

        private String name;
        private Double value;
        private UUID userToken;
        private String type;


        @JsonCreator
        public MyOtherEvent(@JsonProperty("name") String name,
                @JsonProperty("value") Double value,
                @JsonProperty("token") UUID token,
                @JsonProperty("type") String type) {
                
            this.name = name;
            this.value = value;
            this.userToken = token;
            this.type = type;
        }
       
        @JsonIgnore
        @Override
        public BusEventType getBusEventType() {
            return BusEventType.valueOf(type);
        }

        @Override
        public UUID getUserToken() {
            return userToken;
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
        public MyEventHandlerException(String msg) {
            super(msg);
        }
    }

    public static class MyEventHandler {

        private final int expectedEvents;

        private volatile int gotEvents;


        public MyEventHandler(int exp) {
            this.expectedEvents = exp;
            this.gotEvents = 0;
        }

        public synchronized int getEvents() {
            return gotEvents;
        }

        @Subscribe
        public synchronized void processEvent(MyEvent event) {
            gotEvents++;
            //log.debug("Got event {} {}", event.name, event.value);
        }

        @Subscribe
        public synchronized void processEvent(MyEventWithException event) {
            throw new MyEventHandlerException("FAIL");
        }
        
        public synchronized boolean waitForCompletion(long timeoutMs) {

            long ini = System.currentTimeMillis();
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
        MyEventHandler handler = new MyEventHandler(1);
        eventBus.register(handler);

        eventBus.post(new MyEventWithException("my-event", 1L, UUID.randomUUID(), BusEventType.ACCOUNT_CHANGE.toString()));
        
        Thread.sleep(50000);
        } catch (Exception e) {
            
        }
        
    }
    
    public void testSimple() {
        try {

            int nbEvents = 5;
            MyEventHandler handler = new MyEventHandler(nbEvents);
            eventBus.register(handler);

            for (int i = 0; i < nbEvents; i++) {
                eventBus.post(new MyEvent("my-event", (long) i, UUID.randomUUID(), BusEventType.ACCOUNT_CHANGE.toString()));
            }

            boolean completed = handler.waitForCompletion(10000);
            Assert.assertEquals(completed, true);
        } catch (Exception e) {
            Assert.fail("",e);
        }
    }

    public void testDifferentType() {
        try {

            MyEventHandler handler = new MyEventHandler(1);
            eventBus.register(handler);

            for (int i = 0; i < 5; i++) {
                eventBus.post(new MyOtherEvent("my-other-event", (double) i, UUID.randomUUID(), BusEventType.BUNDLE_REPAIR.toString()));
            }
            eventBus.post(new MyEvent("my-event", 11l, UUID.randomUUID(), BusEventType.ACCOUNT_CHANGE.toString()));

            boolean completed = handler.waitForCompletion(10000);
            Assert.assertEquals(completed, true);
        } catch (Exception e) {
            Assert.fail("",e);
        }

    }
}
