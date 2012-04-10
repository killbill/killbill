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

package com.ning.billing.payment.util;

import static org.testng.Assert.assertEquals;

import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.InMemoryBus;

@Test
public class TestSyncWaitOnEventBus {
    private static final class TestEvent implements EventBusRequest<UUID> {
        private final UUID id;
        private final String msg;

        public TestEvent(UUID id, String msg) {
            this.id = id;
            this.msg = msg;
        }

        @Override
        public UUID getId() {
            return id;
        }

        public String getMsg() {
            return msg;
        }

		@Override
		public BusEventType getBusEventType() {
			return null;
		}
    }

    private static final class TestResponse implements EventBusResponse<UUID> {
        private final UUID id;
        private final String msg;

        public TestResponse(UUID id, String msg) {
            this.id = id;
            this.msg = msg;
        }

        @Override
        public UUID getRequestId() {
            return id;
        }

        public String getMsg() {
            return msg;
        }

		@Override
		public BusEventType getBusEventType() {
			return null;
		}
    }

    private Bus eventBus;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        eventBus = new InMemoryBus();
        eventBus.start();
        eventBus.register(new Object() {
            @Subscribe
            public void handleEvent(TestEvent event) throws Exception {
                Thread.sleep(100);
                eventBus.post(new TestResponse(event.getId(), event.getMsg()));
            }
        });
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        eventBus.stop();
    }

    public void test() throws Exception {
        final TestEvent event = new TestEvent(UUID.randomUUID(), "Hello World!");

        Future<TestResponse> future = EventBusFuture.post(eventBus, event);
        TestResponse response = future.get(1, TimeUnit.SECONDS);

        assertEquals(response.getRequestId(), event.getId());
        assertEquals(response.getMsg(), event.getMsg());
    }
}
