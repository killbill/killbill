/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.mock.glue;

import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.ObjectType;
import com.ning.billing.dao.MockNonEntityDao;
import com.ning.billing.util.cache.CacheController;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.dao.TableName;

import com.google.inject.AbstractModule;

public class MockNonEntityDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NonEntityDao.class).to(MockNonEntityDao.class);
    }
}
