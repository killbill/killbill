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

package com.ning.billing.util.tag;


import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;

import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.util.glue.TagStoreModule;

public class MockTagStoreModuleSql extends TagStoreModule {
    private MysqlTestingHelper helper;

    @Override
    protected void configure() {
        helper = new MysqlTestingHelper();
        bind(IDBI.class).toInstance(helper.getDBI());
        bind(MysqlTestingHelper.class).toInstance(helper);
        install(new MockClockModule());
        super.configure();
    }

    public void execute(final String ddl) {
        helper.getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute(ddl);
                return null;
            }
        });
    }
}
