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
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.tag.api.DefaultTagUserApi;
import com.ning.billing.util.tag.dao.AuditedTagDao;
import com.ning.billing.util.tag.dao.DefaultTagDefinitionDao;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

public class TagStoreModule extends AbstractModule {
    protected void installDaos() {
        bind(TagDefinitionDao.class).to(DefaultTagDefinitionDao.class).asEagerSingleton();
        bind(TagDao.class).to(AuditedTagDao.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installDaos();
        bind(TagUserApi.class).to(DefaultTagUserApi.class).asEagerSingleton();
    }
}
