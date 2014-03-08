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

package org.killbill.billing.util.export.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.tweak.HandleCallback;

import org.killbill.billing.util.api.ColumnInfo;
import org.killbill.billing.util.api.DatabaseExportOutputStream;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.validation.DefaultColumnInfo;
import org.killbill.billing.util.validation.dao.DatabaseSchemaDao;

@Singleton
public class DatabaseExportDao {

    private final DatabaseSchemaDao databaseSchemaDao;
    private final IDBI dbi;

    @Inject
    public DatabaseExportDao(final DatabaseSchemaDao databaseSchemaDao,
                             final IDBI dbi) {
        this.databaseSchemaDao = databaseSchemaDao;
        this.dbi = dbi;
    }

    public void exportDataForAccount(final DatabaseExportOutputStream out, final InternalTenantContext context) {
        if (context.getAccountRecordId() == null || context.getTenantRecordId() == null) {
            return;
        }

        final List<DefaultColumnInfo> columns = databaseSchemaDao.getColumnInfoList();
        if (columns.size() == 0) {
            return;
        }

        final List<ColumnInfo> columnsForTable = new ArrayList<ColumnInfo>();
        // The list of columns is ordered by table name first
        String lastSeenTableName = columns.get(0).getTableName();
        for (final ColumnInfo column : columns) {
            if (!column.getTableName().equals(lastSeenTableName)) {
                exportDataForAccountAndTable(out, columnsForTable, context);
                lastSeenTableName = column.getTableName();
                columnsForTable.clear();
            }
            columnsForTable.add(column);
        }
        exportDataForAccountAndTable(out, columnsForTable, context);
    }

    private void exportDataForAccountAndTable(final DatabaseExportOutputStream out, final List<ColumnInfo> columnsForTable, final InternalTenantContext context) {
        boolean hasAccountRecordIdColumn = false;
        boolean firstColumn = true;
        final StringBuilder queryBuilder = new StringBuilder("select ");
        for (final ColumnInfo column : columnsForTable) {
            if (!firstColumn) {
                queryBuilder.append(", ");
            } else {
                firstColumn = false;
            }

            queryBuilder.append(column.getColumnName());
            if (column.getColumnName().equals("account_record_id")) {
                hasAccountRecordIdColumn = true;
            }
        }

        final String tableName = columnsForTable.get(0).getTableName();
        final boolean isAccountTable = TableName.ACCOUNT.getTableName().equals(tableName);

        // Don't export non-account specific tables
        if (!isAccountTable && !hasAccountRecordIdColumn) {
            return;
        }

        // Build the query - make sure to filter by account and tenant!
        queryBuilder.append(" from ")
                    .append(tableName);
        if (isAccountTable) {
            queryBuilder.append(" where record_id = :accountRecordId and tenant_record_id = :tenantRecordId");
        } else {
            queryBuilder.append(" where account_record_id = :accountRecordId and tenant_record_id = :tenantRecordId");
        }

        // Notify the stream that we're about to write data for a different table
        out.newTable(tableName, columnsForTable);

        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                final ResultIterator<Map<String, Object>> iterator = handle.createQuery(queryBuilder.toString())
                                                                           .bind("accountRecordId", context.getAccountRecordId())
                                                                           .bind("tenantRecordId", context.getTenantRecordId())
                                                                           .iterator();
                try {
                    while (iterator.hasNext()) {
                        final Map<String, Object> row = iterator.next();
                        out.write(row);
                    }
                } finally {
                    iterator.close();
                }

                return null;
            }
        });
    }
}
