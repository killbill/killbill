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
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.api.ColumnInfo;
import org.killbill.billing.util.api.DatabaseExportOutputStream;
import org.killbill.billing.util.config.definition.ExportConfig;
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

    private final ExportConfig exportConfig;
    private final IDBI dbi;

    @Inject
    public DatabaseExportDao(final DatabaseSchemaDao databaseSchemaDao,
                             final ExportConfig exportConfig,
                             final IDBI dbi) {
        this.databaseSchemaDao = databaseSchemaDao;
        this.exportConfig = exportConfig;
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

        /* extra tables */
        EXTRA("account_id", "tenant_id"),
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

    public void exportDataForAccount(final DatabaseExportOutputStream out, final UUID accountId, final UUID tenantId, final InternalTenantContext context) {
        if (context.getAccountRecordId() == null || context.getTenantRecordId() == null) {
            return;
        }

        final List<DefaultColumnInfo> columns = databaseSchemaDao.getColumnInfoList();
        if (columns.size() == 0) {
            return;
        }

        final List<ColumnInfo> columnsForTable = new ArrayList<ColumnInfo>();
        // Separate lookup table, to keep the ordering of the columns
        final Map<String, Integer> columnsLookup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // Ignore casing (for H2)

        // The list of columns is ordered by table name first
        String lastSeenTableName = columns.get(0).getTableName();
        int j = 0;
        for (final ColumnInfo column : columns) {
            if (!column.getTableName().equals(lastSeenTableName)) {
                exportDataForAccountAndTable(out, columnsForTable, columnsLookup, accountId, tenantId, context);
                lastSeenTableName = column.getTableName();
                columnsForTable.clear();
                columnsLookup.clear();
                j = 0;
            }
            columnsForTable.add(column);
            columnsLookup.put(column.getColumnName(), j);
            j++;
        }
        exportDataForAccountAndTable(out, columnsForTable, columnsLookup, accountId, tenantId, context);
    }


    private void exportDataForAccountAndTable(final DatabaseExportOutputStream out,
                                              final List<ColumnInfo> columnsForTable,
                                              final Map<String, Integer> columnsLookup,
                                              final UUID accountId,
                                              final UUID tenantId,
                                              final InternalTenantContext context) {


        TableType tableType = TableType.OTHER;
        final String tableName = columnsForTable.get(0).getTableName();

        // Ignore casing (for H2)
        if (TableName.ACCOUNT.getTableName().equalsIgnoreCase(tableName)) {
            tableType = TableType.KB_ACCOUNT;
        } else if (TableName.ACCOUNT_HISTORY.getTableName().equalsIgnoreCase(tableName)) {
            tableType = TableType.KB_ACCOUNT_HISTORY;
        } else if(exportConfig.getExtraTablesPrefix() != null && !exportConfig.getExtraTablesPrefix().isEmpty() && exportConfig.getExtraTablesPrefix().stream().anyMatch(prefix -> tableName.toLowerCase().startsWith(prefix))) {
            tableType = TableType.EXTRA;
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
                // Ignore casing (for H2)
                if (column.getColumnName().equalsIgnoreCase(TableType.KB_PER_ACCOUNT.getAccountRecordIdColumnName())) {
                    tableType = TableType.KB_PER_ACCOUNT;
                } else if (column.getColumnName().equalsIgnoreCase(TableType.NOTIFICATION.getAccountRecordIdColumnName())) {
                    tableType = TableType.NOTIFICATION;
                }
            }
        }

        // Don't export non-account specific tables
        if (tableType == TableType.OTHER) {
            return;
        }

        if (tableType == TableType.EXTRA) {
            queryBuilder.append(" from ")
                        .append(tableName)
                        .append(" where ")
                        .append(tableType.getTenantRecordIdColumnName())
                        .append("  = :tenantRecordId and (")
                        .append(tableType.getAccountRecordIdColumnName())
                        .append(" = :accountRecordId OR ")
                        .append(tableType.getAccountRecordIdColumnName()) //TODO_354 - Custom logic for aviate_catalog, to include tenant level entries when accountId is null
                        .append(" is null)")
            ;

        } else {

            // Build the query - make sure to filter by account and tenant!
            queryBuilder.append(" from ")
                        .append(tableName)
                        .append(" where ")
                        .append(tableType.getAccountRecordIdColumnName())
                        .append(" = :accountRecordId and ")
                        .append(tableType.getTenantRecordIdColumnName())
                        .append("  = :tenantRecordId");
        }

        // Notify the stream that we're about to write data for a different table
        out.newTable(tableName, columnsForTable);
        final TableType finalTableType = tableType;
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                final ResultIterator<Map<String, Object>> iterator = handle.createQuery(queryBuilder.toString())
                                                                           .bind("accountRecordId", finalTableType == TableType.EXTRA ? accountId : context.getAccountRecordId())
                                                                           .bind("tenantRecordId", finalTableType == TableType.EXTRA ? tenantId : context.getTenantRecordId())
                                                                           .iterator();
                try {
                    while (iterator.hasNext()) {
                        final Map<String, Object> row = iterator.next();

                        for (final Entry<String, Object> entry : row.entrySet()) {
                            final String k = entry.getKey();
                            final Object value = entry.getValue();
                            // For h2, transform a JdbcBlob and a JdbcClob into a byte[]
                            // See also LowerToCamelBeanMapper
                            if (value instanceof Blob) {
                                final Blob blob = (Blob) value;
                                row.put(k, blob.getBytes(1, (int) blob.length()));
                            } else if (value instanceof Clob) {
                                // TODO Update LowerToCamelBeanMapper?
                                final Clob clob = (Clob) value;
                                row.put(k, clob.getSubString(1, (int) clob.length()));
                            } else if (value != null &&
                                       columnsLookup.get(k) != null &&
                                       columnsForTable.get(columnsLookup.get(k)) != null &&
                                       "boolean".equals(columnsForTable.get(columnsLookup.get(k)).getDataType())) {
                                row.put(k, value instanceof Boolean ? value : "1".equals(value.toString())); // Most likely Byte
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
