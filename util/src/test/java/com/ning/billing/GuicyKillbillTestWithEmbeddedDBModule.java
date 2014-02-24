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

package com.ning.billing;

import java.io.IOException;

import javax.sql.DataSource;

import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;

import com.ning.billing.commons.embeddeddb.EmbeddedDB;

public class GuicyKillbillTestWithEmbeddedDBModule extends GuicyKillbillTestModule {

    @Override
    protected void configure() {
        super.configure();

        final EmbeddedDB instance = DBTestingHelper.get();
        bind(EmbeddedDB.class).toInstance(instance);

        try {
            bind(DataSource.class).toInstance(DBTestingHelper.get().getDataSource());
            bind(IDBI.class).toInstance(DBTestingHelper.getDBI());
        } catch (final IOException e) {
            Assert.fail(e.toString());
        }
    }
}
