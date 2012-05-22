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

package com.ning.billing.beatrix.integration.overdue;

import com.google.inject.Inject;
import com.ning.billing.ovedue.notification.OverdueCheckNotifier;
import com.ning.billing.overdue.OverdueProperties;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.listener.OverdueListener;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.util.bus.BusService;

public class MockOverdueService extends DefaultOverdueService {
    @Inject
    public MockOverdueService(OverdueUserApi userApi, OverdueProperties properties, OverdueCheckNotifier notifier,
            BusService busService, OverdueListener listener, OverdueWrapperFactory factory) {
        super(userApi, properties, notifier, busService, listener, factory);
    }

    public synchronized void loadConfig() throws ServiceException {
       
    }
 
}