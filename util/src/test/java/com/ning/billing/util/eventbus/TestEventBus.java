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

package com.ning.billing.util.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;

public class TestEventBus {

    private static final Logger log = LoggerFactory.getLogger(TestEventBus.class);

    private EventBus eventBus;


    @BeforeClass
    public void setup() {
        eventBus = new MemoryEventBus();
        eventBus.start();
    }

    @AfterClass
    public void tearDown() {
        eventBus.stop();
    }

    public static final class MyEvent implements EventBusNotification {
        String name;
        Long value;

        public MyEvent(String name, Long value) {
            this.name = name;
            this.value = value;
        }
    }

    public static class MyEventHandler {

        private final int expectedEvents;

        private int gotEvents;


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
            log.info("Got event {} {}", event.name, event.value);
        }

        public synchronized boolean waitForCompletion(long timeoutMs) {

            while (gotEvents < expectedEvents) {
                try {
                    wait(timeoutMs);
                    break;
                } catch (InterruptedException ignore) {
                }
            }
            return (gotEvents == expectedEvents);
        }
    }

    @Test()
    public void test() {
        try {

            int nbEvents = 127;
            MyEventHandler handler = new MyEventHandler(nbEvents);
            eventBus.register(handler);

            for (int i = 0; i < nbEvents; i++) {
                eventBus.post(new MyEvent("my-event", (long) i));
            }

            boolean completed = handler.waitForCompletion(3000);
            Assert.assertEquals(completed, true);
        } catch (Exception e) {
            Assert.fail("",e);
        }

    }
}
