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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.FlywayCallback;
import org.flywaydb.core.api.resolver.MigrationExecutor;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.internal.command.DbMigrate;
import org.flywaydb.core.internal.dbsupport.DbSupport;
import org.flywaydb.core.internal.dbsupport.DbSupportFactory;
import org.flywaydb.core.internal.dbsupport.Schema;
import org.flywaydb.core.internal.dbsupport.SqlStatement;
import org.flywaydb.core.internal.dbsupport.Table;
import org.flywaydb.core.internal.info.MigrationInfoImpl;
import org.flywaydb.core.internal.info.MigrationInfoServiceImpl;
import org.flywaydb.core.internal.metadatatable.AppliedMigration;
import org.flywaydb.core.internal.metadatatable.MetaDataTable;
import org.flywaydb.core.internal.util.PlaceholderReplacer;
import org.flywaydb.core.internal.util.jdbc.TransactionCallback;
import org.flywaydb.core.internal.util.jdbc.TransactionTemplate;
import org.flywaydb.core.internal.util.logging.Log;
import org.flywaydb.core.internal.util.logging.LogFactory;
import org.flywaydb.core.internal.util.scanner.filesystem.FileSystemResource;

public class DbMigrateWithDryRun extends DbMigrate {

    private static final Log LOG = LogFactory.getLog(DbMigrateWithDryRun.class);

    private final List<SqlStatement> sqlStatements;
    private final PlaceholderReplacer placeholderReplacer;
    private final String encoding;
    private final MigrationVersion target;
    private final DbSupport dbSupport;
    private final MetaDataTable metaDataTableForDryRun;
    private final Schema schema;
    private final MigrationResolver migrationResolver;
    private final Connection connectionMetaDataTable;
    private final Connection connectionUserObjects;
    private final boolean outOfOrder;
    private final FlywayCallback[] callbacks;
    private final DbSupport dbSupportUserObjects;

    /**
     * Creates a new database migrator.
     *
     * @param sqlStatements               The current list of all pending migrations.
     * @param placeholderReplacer         The placeholder replacer to apply to sql migration scripts.
     * @param encoding                    The encoding of Sql migrations.
     * @param metaDataDBTable             The database metadata DB Table.
     * @param connectionMetaDataTable     The connection to use.
     * @param connectionUserObjects       The connection to use to perform the actual database migrations.
     * @param dbSupport                   Database-specific functionality.
     * @param metaDataTable               The database metadata table.
     * @param migrationResolver           The migration resolver.
     * @param target                      The target version of the migration.
     * @param ignoreFutureMigrations      Flag whether to ignore future migrations or not.
     * @param ignoreFailedFutureMigration Flag whether to ignore failed future migrations or not.
     * @param outOfOrder                  Allows migrations to be run "out of order".
     */
    public DbMigrateWithDryRun(final List<SqlStatement> sqlStatements,
                               final PlaceholderReplacer placeholderReplacer,
                               final String encoding,
                               final Table metaDataDBTable,
                               final Connection connectionMetaDataTable,
                               final Connection connectionUserObjects,
                               final DbSupport dbSupport,
                               final MetaDataTable metaDataTable,
                               final Schema schema,
                               final MigrationResolver migrationResolver,
                               final MigrationVersion target,
                               final boolean ignoreFutureMigrations,
                               final boolean ignoreFailedFutureMigration,
                               final boolean outOfOrder,
                               final FlywayCallback[] callbacks) {
        super(connectionMetaDataTable, connectionUserObjects, dbSupport, metaDataTable, schema, migrationResolver, target, ignoreFutureMigrations, ignoreFailedFutureMigration, outOfOrder, callbacks);
        this.sqlStatements = sqlStatements;
        this.placeholderReplacer = placeholderReplacer;
        this.encoding = encoding;
        this.connectionMetaDataTable = connectionMetaDataTable;
        this.connectionUserObjects = connectionUserObjects;
        this.dbSupport = dbSupport;
        this.schema = schema;
        this.migrationResolver = migrationResolver;
        this.target = target;
        this.outOfOrder = outOfOrder;
        this.callbacks = callbacks;

        this.dbSupportUserObjects = DbSupportFactory.createDbSupport(connectionUserObjects, false);

        // PIERRE: change MetaDataTable to capture the SQL
        this.metaDataTableForDryRun = new CapturingMetaDataTable(sqlStatements, dbSupport, metaDataDBTable);
    }

    public int dryRunMigrate() throws FlywayException {
        try {
            for (final FlywayCallback callback : callbacks) {
                new TransactionTemplate(connectionUserObjects).execute(new TransactionCallback<Object>() {
                    @Override
                    public Object doInTransaction() throws SQLException {
                        dbSupportUserObjects.changeCurrentSchemaTo(schema);
                        callback.beforeMigrate(connectionUserObjects);
                        return null;
                    }
                });
            }

            // PIERRE: perform a single query to the metadata table
            final MigrationInfoServiceImpl infoService = new MigrationInfoServiceImpl(migrationResolver, metaDataTableForDryRun, target, outOfOrder, true, true);
            infoService.refresh();

            final MigrationInfoImpl[] pendingMigrations = infoService.pending();
            new TransactionTemplate(connectionMetaDataTable, false).execute(new TransactionCallback<Boolean>() {
                public Boolean doInTransaction() {
                    int i = 1;
                    for (final MigrationInfoImpl migrationInfo : pendingMigrations) {
                        applyMigration(i, migrationInfo);
                        i++;
                    }

                    return true;
                }
            });

            for (final FlywayCallback callback : callbacks) {
                new TransactionTemplate(connectionUserObjects).execute(new TransactionCallback<Object>() {
                    @Override
                    public Object doInTransaction() throws SQLException {
                        dbSupportUserObjects.changeCurrentSchemaTo(schema);
                        callback.afterMigrate(connectionUserObjects);
                        return null;
                    }
                });
            }

            return pendingMigrations.length;
        } finally {
            dbSupportUserObjects.restoreCurrentSchema();
        }
    }

    private void applyMigration(final int installedRnk, final MigrationInfoImpl migration) {
        final MigrationVersion version = migration.getVersion();
        final String migrationText;
        if (version != null) {
            migrationText = "schema " + schema + " to version " + version + " - " + migration.getDescription();
        } else {
            migrationText = "schema " + schema + " with repeatable migration " + migration.getDescription();
        }
        LOG.info("Migrating " + migrationText);

        // PIERRE: override the executor to capture the SQL
        final FileSystemResource sqlScriptResource = new FileSystemResource(migration.getResolvedMigration().getPhysicalLocation());
        final MigrationExecutor migrationExecutor = new CapturingSqlMigrationExecutor(sqlStatements,
                                                                                      dbSupport,
                                                                                      sqlScriptResource,
                                                                                      placeholderReplacer,
                                                                                      encoding);
        try {
            doMigrate(migration, migrationExecutor, migrationText);
        } catch (final SQLException e) {
            throw new FlywayException("Unable to apply migration", e);
        }

        final AppliedMigration appliedMigration = new AppliedMigration(installedRnk,
                                                                       version,
                                                                       migration.getDescription(),
                                                                       migration.getType(),
                                                                       migration.getScript(),
                                                                       migration.getResolvedMigration().getChecksum(),
                                                                       null,
                                                                       null,
                                                                       -1,
                                                                       true);
        metaDataTableForDryRun.addAppliedMigration(appliedMigration);
    }

    private void doMigrate(final MigrationInfo migration, final MigrationExecutor migrationExecutor, final String migrationText) throws SQLException {
        for (final FlywayCallback callback : callbacks) {
            dbSupportUserObjects.changeCurrentSchemaTo(schema);
            callback.beforeEachMigrate(connectionUserObjects, migration);
        }

        dbSupportUserObjects.changeCurrentSchemaTo(schema);
        migrationExecutor.execute(connectionUserObjects);
        LOG.debug("Successfully completed migration of " + migrationText);

        for (final FlywayCallback callback : callbacks) {
            dbSupportUserObjects.changeCurrentSchemaTo(schema);
            callback.afterEachMigrate(connectionUserObjects, migration);
        }
    }
}
