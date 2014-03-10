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

import org.osgi.util.tracker.ServiceTracker;

public abstract class OSGIKillbillLibraryBase {


    public OSGIKillbillLibraryBase() {

    }

    public abstract void close();


    protected abstract class APICallback<API, T> {

        private final String serviceName;

        protected APICallback(final String serviceName) {
            this.serviceName = serviceName;
        }

        public abstract API executeWithService(T service);

        protected API executeWithNoService() {
            throw new OSGIServiceNotAvailable(serviceName);
        }
    }

    protected <API, S, T> API withServiceTracker(ServiceTracker<S, T> t, APICallback<API, T> cb) {
        T service = t.getService();
        if (service == null) {
            return cb.executeWithNoService();
        }
        return cb.executeWithService(service);
    }
}
