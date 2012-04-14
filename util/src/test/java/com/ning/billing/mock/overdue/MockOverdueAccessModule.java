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

package com.ning.billing.mock.overdue;

import com.google.inject.AbstractModule;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.overdue.OverdueAccessApi;
import com.ning.billing.util.overdue.dao.OverdueAccessDao;

public class MockOverdueAccessModule extends AbstractModule {
    public static final String CLEAR_STATE="Clear";

    @Override
    protected void configure() {
        OverdueAccessApi overdueAccessApi = BrainDeadProxyFactory.createBrainDeadProxyFor(OverdueAccessApi.class);
        ((ZombieControl) overdueAccessApi).addResult("getOverdueStateNameFor", MockOverdueAccessModule.CLEAR_STATE);
        bind(OverdueAccessDao.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(OverdueAccessDao.class));
        bind(OverdueAccessApi.class).toInstance(overdueAccessApi);
    }
}
