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

package com.ning.billing.tenant.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.tenant.api.DefaultTenant;
import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.util.dao.MapperBase;

public class TenantMapper extends MapperBase implements ResultSetMapper<Tenant> {

    @Override
    public Tenant map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
        final UUID id = getUUID(result, "id");
        final String externalKey = result.getString("external_key");
        final String apiKey = result.getString("api_key");
        final DateTime createdDate = getDateTime(result, "created_date");
        final DateTime updatedDate = getDateTime(result, "updated_date");

        return new DefaultTenant(id, createdDate, updatedDate, externalKey, apiKey, null);
    }
}
