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
import java.util.List;

import org.flywaydb.core.api.resolver.MigrationExecutor;
import org.flywaydb.core.internal.dbsupport.DbSupport;
import org.flywaydb.core.internal.dbsupport.SqlScript;
import org.flywaydb.core.internal.dbsupport.SqlStatement;
import org.flywaydb.core.internal.util.PlaceholderReplacer;
import org.flywaydb.core.internal.util.scanner.Resource;

public class CapturingSqlMigrationExecutor implements MigrationExecutor {

    private final List<SqlStatement> sqlStatements;
    private final DbSupport dbSupport;
    private final PlaceholderReplacer placeholderReplacer;
    private final Resource sqlScriptResource;
    private final String encoding;

    /**
     * Creates a new sql script migration based on this sql script.
     *
     * @param sqlStatements       The current list of all pending migrations.
     * @param dbSupport           The database-specific support.
     * @param sqlScriptResource   The resource containing the sql script.
     * @param placeholderReplacer The placeholder replacer to apply to sql migration scripts.
     * @param encoding            The encoding of this Sql migration.
     */
    public CapturingSqlMigrationExecutor(final List<SqlStatement> sqlStatements,
                                         final DbSupport dbSupport,
                                         final Resource sqlScriptResource,
                                         final PlaceholderReplacer placeholderReplacer,
                                         final String encoding) {
        this.sqlStatements = sqlStatements;
        this.dbSupport = dbSupport;
        this.sqlScriptResource = sqlScriptResource;
        this.encoding = encoding;
        this.placeholderReplacer = placeholderReplacer;
    }

    @Override
    public void execute(final Connection connection) {
        final SqlScript sqlScript = new SqlScript(dbSupport, sqlScriptResource, placeholderReplacer, encoding);
        for (final SqlStatement sqlStatement : sqlScript.getSqlStatements()) {
            sqlStatements.add(sqlStatement);
        }
    }

    @Override
    public boolean executeInTransaction() {
        return true;
    }
}
