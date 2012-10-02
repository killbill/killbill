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

package com.ning.billing.util.callcontext;

import org.skife.jdbi.v2.sqlobject.Bind;

public class MockCallContextSqlDao implements CallContextSqlDao {

    @Override
    public Long getTenantRecordId(@Bind("tenantId") final String tenantId) {
        return InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID;
    }

    @Override
    public Long getAccountRecordId(@Bind("accountId") final String accountId) {
        return InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID;
    }
}
