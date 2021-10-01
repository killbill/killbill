/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.optimizer;

import java.sql.Connection;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.BusEventWithMetadata;
import org.killbill.bus.api.PersistentBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BusOptimizerNoop implements BusOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(BusOptimizerNoop.class);

    private final PersistentBus delegate;

    @Inject
    public BusOptimizerNoop(final PersistentBus eventBus) {
        this.delegate = eventBus;
        logger.info("Feature BusOptimizer is OFF");
    }

    @Override
    public void register(final Object handlerInstance) throws EventBusException {
        delegate.register(handlerInstance);
    }

    @Override
    public void unregister(final Object handlerInstance) throws EventBusException {
        delegate.unregister(handlerInstance);
    }

    @Override
    public void post(final BusEvent event) throws EventBusException {
        delegate.post(event);
    }

    @Override
    public void postFromTransaction(final BusEvent event, final Connection connection) throws EventBusException {
        delegate.postFromTransaction(event, connection);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return delegate.getAvailableBusEventsForSearchKeys(searchKey1, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        return delegate.getAvailableBusEventsFromTransactionForSearchKeys(searchKey1, searchKey2, connection);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2) {
        return delegate.getAvailableBusEventsForSearchKey2(maxCreatedDate, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2, final Connection connection) {
        return delegate.getAvailableBusEventsFromTransactionForSearchKey2(maxCreatedDate, searchKey2, connection);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getInProcessingBusEvents() {
        return delegate.getInProcessingBusEvents();
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return delegate.getAvailableOrInProcessingBusEventsForSearchKeys(searchKey1, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        return delegate.getAvailableOrInProcessingBusEventsFromTransactionForSearchKeys(searchKey1, searchKey2, connection);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2) {
        return delegate.getAvailableOrInProcessingBusEventsForSearchKey2(maxCreatedDate, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2, final Connection connection) {
        return delegate.getAvailableOrInProcessingBusEventsFromTransactionForSearchKey2(maxCreatedDate, searchKey2, connection);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getHistoricalBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return delegate.getHistoricalBusEventsForSearchKeys(searchKey1, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getHistoricalBusEventsForSearchKey2(final DateTime minCreatedDate, final Long searchKey2) {
        return delegate.getHistoricalBusEventsForSearchKey2(minCreatedDate, searchKey2);
    }

    @Override
    public long getNbReadyEntries(final DateTime maxCreatedDate) {
        return delegate.getNbReadyEntries(maxCreatedDate);
    }

    @Override
    public boolean initQueue() {
        return delegate.initQueue();
    }

    @Override
    public boolean startQueue() {
        return delegate.startQueue();
    }

    @Override
    public boolean stopQueue() {
        return delegate.stopQueue();
    }

    @Override
    public boolean isStarted() {
        return delegate.isStarted();
    }

    @Override
    public boolean shouldAggregateSubscriptionEvents(final InternalCallContext context) {
        return false;
    }
}
