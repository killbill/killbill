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

package com.ning.billing.junction;

import com.google.inject.AbstractModule;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;

public class MockBlockingModule extends AbstractModule {
    public static final String CLEAR_STATE = "Clear";

    @Override
    protected void configure() {
        final BlockingApi BlockingApi = BrainDeadProxyFactory.createBrainDeadProxyFor(BlockingApi.class);
        ((ZombieControl) BlockingApi).addResult("getOverdueStateNameFor", MockBlockingModule.CLEAR_STATE);
        bind(BlockingStateDao.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(BlockingStateDao.class));
        bind(BlockingApi.class).toInstance(BlockingApi);
    }
}
