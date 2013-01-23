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

package com.ning.billing.dbi;

import java.io.IOException;
import java.util.List;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.io.IOUtils;

import com.google.common.io.Resources;

public abstract class DBTestingHelper {

    private static final Logger log = LoggerFactory.getLogger(DBTestingHelper.class);

    public static final String DB_NAME = "killbill";
    public static final String USERNAME = "root";
    public static final String PASSWORD = "root";

    public enum DBEngine {
        MYSQL,
        H2
    }

    // Discover dynamically list of all tables in that database
    protected List<String> allTables;
    protected IDBI dbiInstance = null;

    public synchronized IDBI getDBI() {
        createInstanceIfNull();
        return dbiInstance;
    }

    public void initDb() throws IOException {

        createInstanceIfNull();

        // We always want the accounts and tenants table
        initDb("drop table if exists accounts;" +
               "CREATE TABLE accounts (\n" +
               "    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,\n" +
               "    id char(36) NOT NULL,\n" +
               "    external_key varchar(128) NULL,\n" +
               "    email varchar(128) NOT NULL,\n" +
               "    name varchar(100) NOT NULL,\n" +
               "    first_name_length int NOT NULL,\n" +
               "    currency char(3) DEFAULT NULL,\n" +
               "    billing_cycle_day_local int DEFAULT NULL,\n" +
               "    billing_cycle_day_utc int DEFAULT NULL,\n" +
               "    payment_method_id char(36) DEFAULT NULL,\n" +
               "    time_zone varchar(50) DEFAULT NULL,\n" +
               "    locale varchar(5) DEFAULT NULL,\n" +
               "    address1 varchar(100) DEFAULT NULL,\n" +
               "    address2 varchar(100) DEFAULT NULL,\n" +
               "    company_name varchar(50) DEFAULT NULL,\n" +
               "    city varchar(50) DEFAULT NULL,\n" +
               "    state_or_province varchar(50) DEFAULT NULL,\n" +
               "    country varchar(50) DEFAULT NULL,\n" +
               "    postal_code varchar(16) DEFAULT NULL,\n" +
               "    phone varchar(25) DEFAULT NULL,\n" +
               "    migrated bool DEFAULT false,\n" +
               "    is_notified_for_invoices boolean NOT NULL,\n" +
               "    created_date datetime NOT NULL,\n" +
               "    created_by varchar(50) NOT NULL,\n" +
               "    updated_date datetime DEFAULT NULL,\n" +
               "    updated_by varchar(50) DEFAULT NULL,\n" +
               "    tenant_record_id int(11) unsigned default null,\n" +
               "    PRIMARY KEY(record_id)\n" +
               ");");
        initDb("drop table if exists tenants; create table tenants(record_id int(11) unsigned not null auto_increment, id char(36) not null, external_key varchar(128) NULL, api_key varchar(128) NULL, " +
               "api_secret varchar(128) NULL, api_salt varchar(128) NULL, created_date datetime NOT NULL, created_by varchar(50) NOT NULL, updated_date datetime DEFAULT NULL, updated_by varchar(50) DEFAULT NULL, primary key(record_id));");

        // We always want the basic tables when we do account_record_id lookups (e.g. for custom fields, tags or junction)
        initDb("drop table if exists bundles; create table bundles(record_id int(11) unsigned not null auto_increment, id char(36) not null, " +
               "account_record_id int(11) unsigned not null, tenant_record_id int(11) unsigned default 0, primary key(record_id));");
        initDb("drop table if exists subscriptions; create table subscriptions(record_id int(11) unsigned not null auto_increment, id char(36) not null, " +
               "account_record_id int(11) unsigned not null, tenant_record_id int(11) unsigned default 0, primary key(record_id));");

        for (final String pack : new String[]{"account", "analytics", "beatrix", "entitlement", "util", "payment", "invoice", "junction", "usage", "meter", "tenant"}) {
            for (final String ddlFile : new String[]{"ddl.sql", "ddl_test.sql"}) {
                final String ddl;
                try {
                    ddl = IOUtils.toString(Resources.getResource("com/ning/billing/" + pack + "/" + ddlFile).openStream());
                } catch (final IllegalArgumentException ignored) {
                    // The test doesn't have this module ddl in the classpath - that's fine
                    continue;
                }
                initDb(ddl);
            }
        }
    }

    public void initDb(final String ddl) throws IOException {
        if (isUsingLocalInstance()) {
            return;
        }
        final IDBI dbi = getDBI();
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                log.debug("Executing DDL script: " + ddl);
                handle.createScript(ddl).execute();
                return null;
            }
        });
    }

    public void cleanupAllTables() {
        final List<String> tablesToCleanup = fetchAllTables();
        for (final String tableName : tablesToCleanup) {
            cleanupTable(tableName);
        }
    }

    public void cleanupTable(final String table) {
        log.debug("Deleting table: " + table);
        final IDBI dbi = getDBI();
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("truncate table " + table);
                return null;
            }
        });
    }

    public String getDbName() {
        return DB_NAME;
    }

    public abstract DBEngine getDBEngine();

    public abstract boolean isUsingLocalInstance();

    // For debugging
    public abstract String getConnectionString();

    // To create the DBI
    public abstract String getJdbcConnectionString();

    public abstract List<String> fetchAllTables();

    public abstract void start() throws IOException;

    public abstract void stop();

    private synchronized void createInstanceIfNull() {
        if (dbiInstance == null) {
            final String dbiString = getJdbcConnectionString();
            dbiInstance = new DBIProvider(dbiString, USERNAME, PASSWORD).get();
        }
    }
}
