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

package com.ning.billing.util.svcsapi.bus;

import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.events.BusInternalEvent;

import com.google.common.eventbus.Subscribe;

/**
 * Bus API based on the guava EventBus API
 * <p/>
 * The API also provides an API to send events from within a transaction
 * with the guarantee that the event will be delivered if and only if
 * the transaction completes. If the implementation is not based on a
 * DB, this API is behaves the same as the regular post() call.
 */
public interface Bus {

    public class EventBusException extends Exception {

        private static final long serialVersionUID = 12355236L;

        public EventBusException() {
            super();
        }

        public EventBusException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public EventBusException(final String message) {
            super(message);
        }
    }

    /**
     * Start accepting events and dispatching them
     */
    public void start();

    /**
     * Stop accepting events and flush event queue before it returns.
     */
    public void stop();

    /**
     * Registers all handler methods on {@code object} to receive events.
     * Handler methods need to be Annotated with {@link Subscribe}
     *
     * @param handlerInstance handler to register
     * @throws EventBusException if bus not been started yet
     */
    public void register(Object handlerInstance) throws EventBusException;

    /**
     * Unregister the handler for a particular type of event
     *
     * @param handlerInstance handler to unregister
     * @throws EventBusException
     */
    public void unregister(Object handlerInstance) throws EventBusException;

    /**
     * Post an event asynchronously
     *
     * @param event   to be posted
     * @param context call context. account record id and tenant id are expected to be populated
     * @throws EventBusException if bus not been started yet
     */
    public void post(BusInternalEvent event, InternalCallContext context) throws EventBusException;

    /**
     * Post an event from within a transaction.
     * Guarantees that the event is persisted on disk from within the same transaction
     *
     * @param event   to be posted
     * @param dao     a valid DAO object obtained through the DBI.onDemand() API.
     * @param context call context. account record id and tenant id are expected to be populated
     * @throws EventBusException if bus not been started yet
     */
    public void postFromTransaction(BusInternalEvent event, Transmogrifier dao, InternalCallContext context) throws EventBusException;
}
