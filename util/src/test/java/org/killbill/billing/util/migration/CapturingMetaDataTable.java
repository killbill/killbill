/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.migration;

import java.sql.SQLException;
import java.util.List;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.internal.dbsupport.DbSupport;
import org.flywaydb.core.internal.dbsupport.SqlStatement;
import org.flywaydb.core.internal.dbsupport.Table;
import org.flywaydb.core.internal.metadatatable.AppliedMigration;
import org.flywaydb.core.internal.metadatatable.MetaDataTableImpl;

public class CapturingMetaDataTable extends MetaDataTableImpl {

    private final List<SqlStatement> sqlStatements;
    private final DbSupport dbSupport;
    private final Table table;

    /**
     * Creates a new instance of the metadata table support.
     *
     * @param sqlStatements The current list of all pending migrations.
     * @param dbSupport     Database-specific functionality.
     * @param table         The metadata table used by flyway.
     */
    public CapturingMetaDataTable(final List<SqlStatement> sqlStatements, final DbSupport dbSupport, final Table table) {
        super(dbSupport, table);
        this.sqlStatements = sqlStatements;
        this.dbSupport = dbSupport;
        this.table = table;
    }

    @Override
    public void addAppliedMigration(final AppliedMigration appliedMigration) {
        final MigrationVersion version = appliedMigration.getVersion();
        final String versionStr = version == null ? null : version.toString();
        final int calculateInstalledRank;
        try {
            calculateInstalledRank = calculateInstalledRank();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }

        final String sql = new StringBuilder().append("INSERT INTO ")
                                              .append(table)
                                              .append(" (")
                                              .append(dbSupport.quote("installed_rank")).append(",")
                                              .append(dbSupport.quote("version")).append(",")
                                              .append(dbSupport.quote("description")).append(",")
                                              .append(dbSupport.quote("type")).append(",")
                                              .append(dbSupport.quote("script")).append(",")
                                              .append(dbSupport.quote("checksum")).append(",")
                                              .append(dbSupport.quote("installed_by")).append(",")
                                              .append(dbSupport.quote("execution_time")).append(",")
                                              .append(dbSupport.quote("success"))
                                              .append(")")
                                              .append(" VALUES (")
                                              .append(calculateInstalledRank + appliedMigration.getInstalledRank()).append(",")
                                              .append("'").append(versionStr).append("',")
                                              .append("'").append(appliedMigration.getDescription()).append("',")
                                              .append("'").append(appliedMigration.getType().name()).append("',")
                                              .append("'").append(appliedMigration.getScript()).append("',")
                                              .append(appliedMigration.getChecksum()).append(",")
                                              .append(dbSupport.getCurrentUserFunction()).append(",")
                                              .append(appliedMigration.getExecutionTime()).append(",")
                                              .append(appliedMigration.isSuccess())
                                              .append(")")
                                              .toString();

        sqlStatements.add(new SqlStatement(0, sql, false));
    }

    /**
     * Calculates the installed rank for the new migration to be inserted.
     *
     * @return The installed rank.
     */
    private int calculateInstalledRank() throws SQLException {
        final int currentMax = dbSupport.getJdbcTemplate().queryForInt("SELECT MAX(" + dbSupport.quote("installed_rank") + ")" + " FROM " + table);
        return currentMax + 1;
    }
}
