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

import java.io.IOException;
import java.sql.Blob;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.api.ColumnInfo;
import org.killbill.billing.util.api.DatabaseExportOutputStream;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.validation.DefaultColumnInfo;
import org.killbill.billing.util.validation.dao.DatabaseSchemaDao;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DatabaseExportDao {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseExportDao.class);

    private final DatabaseSchemaDao databaseSchemaDao;
    private final IDBI dbi;

    @Inject
    public DatabaseExportDao(final DatabaseSchemaDao databaseSchemaDao,
                             final IDBI dbi) {
        this.databaseSchemaDao = databaseSchemaDao;
        this.dbi = dbi;
    }

    private enum TableType {
        /* TableName.ACCOUNT */
        KB_ACCOUNT("record_id", "tenant_record_id"),
        /* TableName.ACCOUNT_HISTORY */
        KB_ACCOUNT_HISTORY("target_record_id", "tenant_record_id"),
        /* Any per-account data table */
        KB_PER_ACCOUNT("account_record_id", "tenant_record_id"),
        /* bus_events, notifications table */
        NOTIFICATION("search_key1", "search_key2"),
        /* To be discarded */
        OTHER(null, null);

        private final String accountRecordIdColumnName;
        private final String tenantRecordIdColumnName;

        TableType(final String accountRecordIdColumnName, final String tenantRecordIdColumnName) {
            this.accountRecordIdColumnName = accountRecordIdColumnName;
            this.tenantRecordIdColumnName = tenantRecordIdColumnName;
        }

        public String getAccountRecordIdColumnName() {
            return accountRecordIdColumnName;
        }

        public String getTenantRecordIdColumnName() {
            return tenantRecordIdColumnName;
        }
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


        TableType tableType = TableType.OTHER;
        final String tableName = columnsForTable.get(0).getTableName();

        if (TableName.ACCOUNT.getTableName().equals(tableName)) {
            tableType = TableType.KB_ACCOUNT;
        } else if (TableName.ACCOUNT_HISTORY.getTableName().equals(tableName)) {
            tableType = TableType.KB_ACCOUNT_HISTORY;
        }

        boolean firstColumn = true;
        final StringBuilder queryBuilder = new StringBuilder("select ");
        for (final ColumnInfo column : columnsForTable) {
            if (!firstColumn) {
                queryBuilder.append(", ");
            } else {
                firstColumn = false;
            }

            queryBuilder.append(column.getColumnName());

            if (tableType == TableType.OTHER) {
                if (column.getColumnName().equals(TableType.KB_PER_ACCOUNT.getAccountRecordIdColumnName())) {
                    tableType = TableType.KB_PER_ACCOUNT;
                } else if (column.getColumnName().equals(TableType.NOTIFICATION.getAccountRecordIdColumnName())) {
                    tableType = TableType.NOTIFICATION;
                }
            }
        }

        // Don't export non-account specific tables
        if (tableType == TableType.OTHER) {
            return;
        }

        // Build the query - make sure to filter by account and tenant!
        queryBuilder.append(" from ")
                    .append(tableName)
                    .append(" where ")
                    .append(tableType.getAccountRecordIdColumnName())
                    .append(" = :accountRecordId and ")
                    .append(tableType.getTenantRecordIdColumnName())
                    .append("  = :tenantRecordId");

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

                        for (final String k : row.keySet()) {
                            final Object value = row.get(k);
                            // For h2, transform a JdbcBlob and a JdbcClob into a byte[]
                            // See also LowerToCamelBeanMapper
                            if (value instanceof Blob) {
                                final Blob blob = (Blob) value;
                                row.put(k, blob.getBytes(0, (int) blob.length()));
                            } else if (value instanceof Clob) {
                                // TODO Update LowerToCamelBeanMapper?
                                final Clob clob = (Clob) value;
                                row.put(k, clob.getSubString(1, (int) clob.length()));
                            }
                        }

                        try {
                            out.write(row);
                        } catch (final IOException e) {
                            logger.warn("Unable to write row: {}", row, e);
                            throw e;
                        }
                    }
                } finally {
                    iterator.close();
                }
                return null;
            }
        });
    }
}
