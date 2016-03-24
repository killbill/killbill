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

import java.util.LinkedList;
import java.util.List;

import org.flywaydb.core.FlywayWithDryRun;
import org.flywaydb.core.internal.dbsupport.SqlStatement;

public class Migrator {

    public static void main(final String[] args) {
        final List<SqlStatement> sqlStatements = new LinkedList<SqlStatement>();

        final FlywayWithDryRun flyway = new FlywayWithDryRun(sqlStatements);
        flyway.configure(System.getProperties());

        flyway.dryRunMigrate();
        // Flush logs
        System.out.flush();

        if (sqlStatements.isEmpty()) {
            return;
        }

        final StringBuilder stringBuffer = new StringBuilder("BEGIN;\n");
        for (final SqlStatement sqlStatement : sqlStatements) {
            stringBuffer.append(sqlStatement.getSql())
                        .append(";\n");
        }
        stringBuffer.append("COMMIT;");
        System.out.println(stringBuffer.toString());
    }
}
