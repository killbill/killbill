/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.overdue;

import javax.inject.Named;

import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.overdue.OverdueInternalApi;
import org.killbill.billing.overdue.OverdueProperties;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.overdue.listener.OverdueListener;
import org.killbill.billing.overdue.notification.OverdueNotifier;
import org.killbill.billing.overdue.service.DefaultOverdueService;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;

import com.google.inject.Inject;

public class MockOverdueService extends DefaultOverdueService {

    @Inject
    public MockOverdueService(final OverdueInternalApi userApi, final OverdueProperties properties,
                              @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_CHECK_NAMED) final OverdueNotifier checkNotifier,
                              @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_ASYNC_BUS_NAMED) final OverdueNotifier asyncNotifier,
                              final BusService busService, final OverdueListener listener, final OverdueWrapperFactory factory) {
        super(userApi, properties, checkNotifier, asyncNotifier, busService, listener, factory);
    }

    public synchronized void loadConfig() throws ServiceException {
    }
}
