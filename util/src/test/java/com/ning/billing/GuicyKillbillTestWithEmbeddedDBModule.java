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


import javax.inject.Provider;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.dbi.H2TestingHelper;
import com.ning.billing.dbi.MysqlTestingHelper;

public class GuicyKillbillTestWithEmbeddedDBModule extends GuicyKillbillTestModule {

    private final static Logger log = LoggerFactory.getLogger(GuicyKillbillTestWithEmbeddedDBModule.class);

    private static DBTestingHelper instance = getDBTestingHelper();

    public static synchronized DBTestingHelper getDBTestingHelper() {
        if (instance == null) {
            if ("true".equals(System.getProperty("com.ning.billing.dbi.test.h2"))) {
                log.info("Using h2 as the embedded database");
                return new H2TestingHelper();
            } else {
                log.info("Using MySQL as the embedded database");
                return new MysqlTestingHelper();
            }
        }
        return instance;
    }

    @Override
    protected void configure() {
        super.configure();
        bind(DBTestingHelper.class).toInstance(instance);
        bind(IDBI.class).toInstance(instance.getDBI());
    }
}
