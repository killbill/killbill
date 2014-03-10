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

package org.killbill.killbill.osgi.libs.killbill;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import org.killbill.billing.notification.plugin.api.ExtBusEvent;

public class OSGIKillbillEventDispatcher extends OSGIKillbillLibraryBase {

    private static final String OBSERVABLE_SERVICE_NAME = "java.util.Observable";

    private final ServiceTracker<Observable, Observable> observableTracker;


    private final Map<OSGIKillbillEventHandler, Observer> handlerToObserver;

    public OSGIKillbillEventDispatcher(BundleContext context) {
        handlerToObserver = new HashMap<OSGIKillbillEventHandler, Observer>();
        observableTracker = new ServiceTracker(context, OBSERVABLE_SERVICE_NAME, null);
        observableTracker.open();
    }

    public void close() {
        if (observableTracker != null) {
            observableTracker.close();
        }
        handlerToObserver.clear();
    }

    public void registerEventHandler(final OSGIKillbillEventHandler handler) {

        withServiceTracker(observableTracker, new APICallback<Void, Observable>(OBSERVABLE_SERVICE_NAME) {
            @Override
            public Void executeWithService(final Observable service) {

                final Observer observer = new Observer() {
                    @Override
                    public void update(final Observable o, final Object arg) {
                        if (!(arg instanceof ExtBusEvent)) {
                            // TODO STEPH or should we throw because that should not happen
                            return;
                        }
                        handler.handleKillbillEvent((ExtBusEvent) arg);
                    }
                };
                handlerToObserver.put(handler, observer);
                service.addObserver(observer);
                return null;
            }
        });
    }

    public void unregisterEventHandler(final OSGIKillbillEventHandler handler) {
        withServiceTracker(observableTracker, new APICallback<Void, Observable>(OBSERVABLE_SERVICE_NAME) {
            @Override
            public Void executeWithService(final Observable service) {

                final Observer observer = handlerToObserver.get(handler);
                if (observer != null) {
                    service.deleteObserver(observer);
                    handlerToObserver.remove(handler);
                }
                return null;
            }
        });

    }

    public interface OSGIKillbillEventHandler {

        public void handleKillbillEvent(final ExtBusEvent killbillEvent);
    }

}
