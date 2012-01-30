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

package com.ning.billing.account.glue;

import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.glue.BusModule;
import org.skife.jdbi.v2.IDBI;

import java.io.IOException;

public class AccountModuleWithEmbeddedDb extends AccountModule {
    private final MysqlTestingHelper helper = new MysqlTestingHelper();

    public void startDb() throws IOException {
        helper.startMysql();
    }

    public void initDb(String ddl) throws IOException {
        helper.initDb(ddl);
    }

    public void stopDb() {
        helper.stopMysql();
    }

    @Override
    protected void configure() {
        bind(IDBI.class).toInstance(helper.getDBI());
        super.configure();
        install(new BusModule());
    }
}
