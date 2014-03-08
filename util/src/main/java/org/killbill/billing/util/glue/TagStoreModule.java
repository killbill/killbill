/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.glue;

import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.tag.DefaultTagInternalApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.tag.api.DefaultTagUserApi;
import org.killbill.billing.util.tag.dao.DefaultTagDao;
import org.killbill.billing.util.tag.dao.DefaultTagDefinitionDao;
import org.killbill.billing.util.tag.dao.TagDao;
import org.killbill.billing.util.tag.dao.TagDefinitionDao;

import com.google.inject.AbstractModule;

public class TagStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        installUserApi();
        installInternalApi();
        installDaos();
    }

    protected void installUserApi() {
        bind(TagUserApi.class).to(DefaultTagUserApi.class).asEagerSingleton();
    }

    protected void installInternalApi() {
        bind(TagInternalApi.class).to(DefaultTagInternalApi.class).asEagerSingleton();
    }

    protected void installDaos() {
        bind(TagDefinitionDao.class).to(DefaultTagDefinitionDao.class).asEagerSingleton();
        bind(TagDao.class).to(DefaultTagDao.class).asEagerSingleton();
    }
}
