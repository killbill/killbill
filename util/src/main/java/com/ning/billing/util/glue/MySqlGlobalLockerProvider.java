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

package com.ning.billing.util.glue;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

import com.ning.billing.commons.locker.GlobalLocker;
import com.ning.billing.commons.locker.mysql.MySqlGlobalLocker;

public class MySqlGlobalLockerProvider implements Provider<GlobalLocker> {

    private final DataSource dataSource;

    @Inject
    public MySqlGlobalLockerProvider(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public GlobalLocker get() {
        return new MySqlGlobalLocker(dataSource);
    }
}
