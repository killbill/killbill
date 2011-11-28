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

package com.ning.billing.account.core;

import com.ning.billing.account.api.IAccountChange;
import com.ning.billing.account.api.IAccountService;
import com.ning.billing.account.api.IAccountUserApi;
import com.ning.billing.lifecycle.LyfecycleHandlerType;
import com.ning.billing.util.eventbus.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class Engine implements IAccountService, IAccountChangeListener {
    private final static Logger log = LoggerFactory.getLogger(Engine.class);

    private static final String ACCOUNT_SERVICE_NAME = "account-service";
    private final IEventBus eventBus;
    private final IAccountUserApi userApi;

    @Inject
    public Engine(IEventBus eventBus, IAccountUserApi userApi) {
        this.eventBus = eventBus;
        this.userApi = userApi;
    }

    @LyfecycleHandlerType(LyfecycleHandlerType.LyfecycleLevel.INIT_SERVICE)
    public void initialize() {
    }

    @LyfecycleHandlerType(LyfecycleHandlerType.LyfecycleLevel.START_SERVICE)
    public void start() {
    }

    @LyfecycleHandlerType(LyfecycleHandlerType.LyfecycleLevel.STOP_SERVICE)
    public void stop() {
    }

    @Override
    public IAccountUserApi getAccountUserApi() {
        return userApi;
    }

    @Override
    public String getName() {
        return ACCOUNT_SERVICE_NAME;
    }

    @Override
    public void processAccountChange(IAccountChange change) {
        try {
            eventBus.post(change);
        } catch (IEventBus.EventBusException e) {
            log.warn("Failed to post account change event.");
        }
    }
}
