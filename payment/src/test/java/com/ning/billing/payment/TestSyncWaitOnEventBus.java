package com.ning.billing.payment;

import static org.testng.Assert.assertEquals;

import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;
import com.ning.billing.util.eventbus.IEventBus;
import com.ning.billing.util.eventbus.MemoryEventBus;

@Test
public class TestSyncWaitOnEventBus {
    private static final class TestEvent implements IEventBusRequestType<UUID> {
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
    }

    private static final class TestResponse implements IEventBusResponseType<UUID> {
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
    }

    private IEventBus eventBus;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        eventBus = new MemoryEventBus();
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
