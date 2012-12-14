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

package com.ning.billing.util.globallocker;

import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.dbi.DBTestingHelper.DBEngine;
import com.ning.billing.mock.glue.MockGlobalLockerModule;
import com.ning.billing.util.glue.GlobalLockerModule;

import com.google.inject.AbstractModule;

public class TestGlobalLockerModule extends AbstractModule {

    private final DBTestingHelper helper;

    public TestGlobalLockerModule(final DBTestingHelper helper) {
        this.helper = helper;
    }

    @Override
    protected void configure() {
        if (DBEngine.MYSQL.equals(helper.getDBEngine())) {
            install(new GlobalLockerModule());
        } else {
            install(new MockGlobalLockerModule());
        }
    }
}
