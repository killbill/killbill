/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.util.customfield;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.customfield.dao.CustomFieldDao;
import org.killbill.billing.util.customfield.dao.MockCustomFieldDao;
import org.killbill.billing.util.glue.CustomFieldModule;

public class MockCustomFieldModuleMemory extends CustomFieldModule {

    public MockCustomFieldModuleMemory(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void installCustomFieldDao() {
        bind(CustomFieldDao.class).to(MockCustomFieldDao.class).asEagerSingleton();
    }
}
