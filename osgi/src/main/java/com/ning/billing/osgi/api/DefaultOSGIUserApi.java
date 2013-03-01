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

package com.ning.billing.osgi.api;

import javax.inject.Inject;

import com.ning.billing.osgi.LiveTracker;

public class DefaultOSGIUserApi implements OSGIUserApi {

    private final LiveTracker liveTracker;

    @Inject
    public DefaultOSGIUserApi(LiveTracker liveTracker) {
        this.liveTracker = liveTracker;
    }

    @Override
    public <S> S getService(final String serviceClassName) throws LiveTrackerException {
        return liveTracker.getRegisteredOSGIService(serviceClassName );
    }
}
