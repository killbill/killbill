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

package org.flywaydb.core;

import java.sql.Connection;
import java.util.List;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.FlywayCallback;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.internal.dbsupport.DbSupport;
import org.flywaydb.core.internal.dbsupport.Schema;
import org.flywaydb.core.internal.dbsupport.SqlStatement;
import org.flywaydb.core.internal.dbsupport.Table;
import org.flywaydb.core.internal.metadatatable.MetaDataTable;
import org.flywaydb.core.internal.util.PlaceholderReplacer;
import org.killbill.billing.util.migration.DbMigrateWithDryRun;

public class FlywayWithDryRun extends Flyway {

    private final List<SqlStatement> sqlStatements;

    public FlywayWithDryRun(final List<SqlStatement> sqlStatements) {
        this.sqlStatements = sqlStatements;
    }

    // Note: we assume the schemas have already been created and baseline() has already been called
    public int dryRunMigrate() throws FlywayException {
        final PlaceholderReplacer placeholderReplacer = new PlaceholderReplacer(getPlaceholders(),
                                                                                getPlaceholderPrefix(),
                                                                                getPlaceholderSuffix());
        return execute(new Command<Integer>() {
            public Integer execute(final Connection connectionMetaDataTable,
                                   final Connection connectionUserObjects,
                                   final MigrationResolver migrationResolver,
                                   final MetaDataTable metaDataTable,
                                   final DbSupport dbSupport,
                                   final Schema[] schemas,
                                   final FlywayCallback[] flywayCallbacks) {
                final Table metaDataDBTable = schemas[0].getTable(getTable());

                final DbMigrateWithDryRun dbMigrate = new DbMigrateWithDryRun(sqlStatements,
                                                                              placeholderReplacer,
                                                                              getEncoding(),
                                                                              metaDataDBTable,
                                                                              connectionMetaDataTable,
                                                                              connectionUserObjects,
                                                                              dbSupport,
                                                                              metaDataTable,
                                                                              schemas[0],
                                                                              migrationResolver,
                                                                              getTarget(),
                                                                              isIgnoreFutureMigrations(),
                                                                              false,
                                                                              isOutOfOrder(),
                                                                              flywayCallbacks);
                return dbMigrate.dryRunMigrate();
            }
        });
    }
}
