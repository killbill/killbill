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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AsyncEventBus;

public class MemoryEventBus implements IEventBus {

    // STEPH config ?
    private final static int MAX_EVENT_THREADS = 13;

    private final static String EVENT_BUS_IDENTIFIER = "eventbus-service";
    private final static String EVENT_BUS_GROUP_NAME = "eventbus-grp";
    private final static String EVENT_BUS_TH_NAME = "eventbus-th";

    private static final Logger log = LoggerFactory.getLogger(MemoryEventBus.class);

    private final EventBusDelegate delegate;
    private final AtomicBoolean isInitialized;

    public class EventBusDelegate extends AsyncEventBus {

        private final Executor executor;
        private final ThreadGroup grp;

        public EventBusDelegate(String name, ThreadGroup grp, Executor executor) {
            super(name, executor);
            this.executor = executor;
            this.grp = grp;
        }

        public void completeDispatch() {
            dispatchQueuedEvents();
        }

        public void stop() {
            // STEPH hum..
        }
    }

    public MemoryEventBus() {

        final ThreadGroup group = new ThreadGroup(EVENT_BUS_GROUP_NAME);
        Executor executor = Executors.newFixedThreadPool(MAX_EVENT_THREADS, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(group, r, EVENT_BUS_TH_NAME);
            }
        });

        this.delegate = new EventBusDelegate(EVENT_BUS_IDENTIFIER, group, executor);
        this.isInitialized = new AtomicBoolean(false);
    }

    @Override
    public void register(Object handlerInstnace) throws EventBusException {
        checkInitialized("register");
        delegate.register(handlerInstnace);
    }

    @Override
    public void post(IEventBusType event) throws EventBusException {
        checkInitialized("post");
        delegate.post(event);
    }

    @Override
    public void postFromTransaction(IEventBusType event, Transmogrifier dao) throws EventBusException {
        checkInitialized("postFromTransaction");
        delegate.post(event);
    }

    @Override
    public void start() {
        if (isInitialized.compareAndSet(false, true)) {
            log.info("MemoryEventBus started...");

        }
    }


    private void checkInitialized(String operation) throws EventBusException {
        if (!isInitialized.get()) {
            throw new EventBusException(String.format("Attempting operation %s on an non initialized eventbus",
                    operation));
        }
    }
    @Override
    public void stop() {
        if (isInitialized.compareAndSet(true, false)) {
            log.info("MemoryEventBus stopping...");
            delegate.completeDispatch();
            log.info("MemoryEventBus completed dispatching events...");
            delegate.stop();
            log.info("MemoryEventBus stoped...");
        }
    }
}
