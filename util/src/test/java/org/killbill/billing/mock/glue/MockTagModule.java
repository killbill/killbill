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

package org.killbill.billing.mock.glue;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.glue.TagStoreModule;
import org.killbill.billing.util.tag.DefaultTagInternalApi;
import org.killbill.billing.util.tag.dao.MockTagDao;
import org.killbill.billing.util.tag.dao.MockTagDefinitionDao;
import org.killbill.billing.util.tag.dao.TagDao;
import org.killbill.billing.util.tag.dao.TagDefinitionDao;
import org.mockito.Mockito;

public class MockTagModule extends TagStoreModule {

    private final boolean mockInternalApi;

    public MockTagModule(final KillbillConfigSource configSource) {
        super(configSource);
        this.mockInternalApi = false;
    }

    public MockTagModule(final KillbillConfigSource configSource, final boolean mockInternalApi) {
        super(configSource);
        this.mockInternalApi = mockInternalApi;
    }

    @Override
    protected void installDaos() {
        bind(TagDefinitionDao.class).to(MockTagDefinitionDao.class).asEagerSingleton();
        bind(TagDao.class).to(MockTagDao.class).asEagerSingleton();
    }

    @Override
    public void installInternalApi() {
        if (mockInternalApi) {
            bind(TagInternalApi.class).toInstance(Mockito.mock(TagInternalApi.class));
        } else {
            bind(TagInternalApi.class).to(DefaultTagInternalApi.class).asEagerSingleton();
        }
    }
}
