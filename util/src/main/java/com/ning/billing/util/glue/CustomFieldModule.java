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

package com.ning.billing.util.glue;

import com.google.inject.AbstractModule;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.customfield.api.DefaultCustomFieldUserApi;
import com.ning.billing.util.customfield.dao.AuditedCustomFieldDao;
import com.ning.billing.util.customfield.dao.CustomFieldDao;

public class CustomFieldModule extends AbstractModule {
    @Override
    protected void configure() {
        installCustomFieldDao();
        installCustomFieldUserApi();
    }

    protected void installCustomFieldUserApi() {
        bind(CustomFieldUserApi.class).to(DefaultCustomFieldUserApi.class).asEagerSingleton();
    }

    protected void installCustomFieldDao() {
        bind(CustomFieldDao.class).to(AuditedCustomFieldDao.class).asEagerSingleton();
    }

}
