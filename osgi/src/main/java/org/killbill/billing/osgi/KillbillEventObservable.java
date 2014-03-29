/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.osgi;

import java.util.Observable;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;

import com.google.common.eventbus.Subscribe;

public class KillbillEventObservable extends Observable {

    private Logger logger = LoggerFactory.getLogger(KillbillEventObservable.class);

    private final PersistentBus externalBus;

    @Inject
    public KillbillEventObservable(@Named("externalBus") final PersistentBus externalBus) {
        this.externalBus = externalBus;
    }

    public void register() throws EventBusException {
        externalBus.register(this);
    }

    public void unregister() throws EventBusException {
        deleteObservers();
        if (externalBus != null) {
            externalBus.unregister(this);
        }
    }

    @Subscribe
    public void handleKillbillEvent(final ExtBusEvent event) {

        logger.debug("Received external event " + event.toString());
        setChanged();
        notifyObservers(event);
    }
}
