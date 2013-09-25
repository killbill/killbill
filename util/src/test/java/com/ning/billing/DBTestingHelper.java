/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing;

import java.io.IOException;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.commons.embeddeddb.EmbeddedDB;
import com.ning.billing.commons.embeddeddb.h2.H2EmbeddedDB;
import com.ning.billing.commons.embeddeddb.mysql.MySQLEmbeddedDB;
import com.ning.billing.commons.embeddeddb.mysql.MySQLStandaloneDB;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.util.io.IOUtils;

import com.google.common.io.Resources;

public class DBTestingHelper {

    private static final Logger log = LoggerFactory.getLogger(DBTestingHelper.class);

    protected static EmbeddedDB instance;

    public static synchronized EmbeddedDB get() {
        if (instance == null) {
            if ("true".equals(System.getProperty("com.ning.billing.dbi.test.h2"))) {
                log.info("Using h2 as the embedded database");
                instance = new H2EmbeddedDB();
            } else {
                if (isUsingLocalInstance()) {
                    log.info("Using MySQL local database");
                    final String databaseName = System.getProperty("com.ning.billing.dbi.test.localDb.database", "killbill");
                    final String username = System.getProperty("com.ning.billing.dbi.test.localDb.password", "root");
                    final String password = System.getProperty("com.ning.billing.dbi.test.localDb.username", "root");
                    instance = new MySQLStandaloneDB(databaseName, username, password);
                } else {
                    log.info("Using MySQL as the embedded database");
                    instance = new MySQLEmbeddedDB();
                }
            }
        }
        return instance;
    }

    public static synchronized IDBI getDBI() throws IOException {
        return new DBIProvider(get().getDataSource()).get();
    }

    public static synchronized void start() throws IOException {
        final EmbeddedDB instance = get();
        instance.initialize();
        instance.start();

        if (isUsingLocalInstance()) {
            return;
        }

        // We always want the accounts and tenants table
        instance.executeScript("drop table if exists accounts;" +
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
        instance.executeScript("DROP TABLE IF EXISTS tenants;\n" +
                               "CREATE TABLE tenants (\n" +
                               "    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,\n" +
                               "    id char(36) NOT NULL,\n" +
                               "    external_key varchar(128) NULL,\n" +
                               "    api_key varchar(128) NULL,\n" +
                               "    api_secret varchar(128) NULL,\n" +
                               "    api_salt varchar(128) NULL,\n" +
                               "    created_date datetime NOT NULL,\n" +
                               "    created_by varchar(50) NOT NULL,\n" +
                               "    updated_date datetime DEFAULT NULL,\n" +
                               "    updated_by varchar(50) DEFAULT NULL,\n" +
                               "    PRIMARY KEY(record_id)\n" +
                               ");");

        // We always want the basic tables when we do account_record_id lookups (e.g. for custom fields, tags or junction)
        instance.executeScript("DROP TABLE IF EXISTS bundles;\n" +
                               "CREATE TABLE bundles (\n" +
                               "    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,\n" +
                               "    id char(36) NOT NULL,\n" +
                               "    external_key varchar(64) NOT NULL,\n" +
                               "    account_id char(36) NOT NULL,\n" +
                               "    last_sys_update_date datetime,\n" +
                               "    created_by varchar(50) NOT NULL,\n" +
                               "    created_date datetime NOT NULL,\n" +
                               "    updated_by varchar(50) NOT NULL,\n" +
                               "    updated_date datetime NOT NULL,\n" +
                               "    account_record_id int(11) unsigned default null,\n" +
                               "    tenant_record_id int(11) unsigned default null,\n" +
                               "    PRIMARY KEY(record_id)\n" +
                               ");");
        instance.executeScript("DROP TABLE IF EXISTS subscriptions;\n" +
                               "CREATE TABLE subscriptions (\n" +
                               "    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,\n" +
                               "    id char(36) NOT NULL,\n" +
                               "    bundle_id char(36) NOT NULL,\n" +
                               "    category varchar(32) NOT NULL,\n" +
                               "    start_date datetime NOT NULL,\n" +
                               "    bundle_start_date datetime NOT NULL,\n" +
                               "    active_version int(11) DEFAULT 1,\n" +
                               "    charged_through_date datetime DEFAULT NULL,\n" +
                               "    paid_through_date datetime DEFAULT NULL,\n" +
                               "    created_by varchar(50) NOT NULL,\n" +
                               "    created_date datetime NOT NULL,\n" +
                               "    updated_by varchar(50) NOT NULL,\n" +
                               "    updated_date datetime NOT NULL,\n" +
                               "    account_record_id int(11) unsigned default null,\n" +
                               "    tenant_record_id int(11) unsigned default null,\n" +
                               "    PRIMARY KEY(record_id)\n" +
                               ");");

        // HACK (PIERRE): required by invoice tests which perform payments lookups to find the account record id for the internal callcontext
        instance.executeScript("DROP TABLE IF EXISTS payments;\n" +
                               "CREATE TABLE payments (\n" +
                               "    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,\n" +
                               "    id char(36) NOT NULL,\n" +
                               "    account_id char(36) NOT NULL,\n" +
                               "    invoice_id char(36) NOT NULL,\n" +
                               "    payment_method_id char(36) NOT NULL,\n" +
                               "    amount numeric(10,4),\n" +
                               "    currency char(3),\n" +
                               "    effective_date datetime,\n" +
                               "    payment_status varchar(50),\n" +
                               "    created_by varchar(50) NOT NULL,\n" +
                               "    created_date datetime NOT NULL,\n" +
                               "    updated_by varchar(50) NOT NULL,\n" +
                               "    updated_date datetime NOT NULL,\n" +
                               "    account_record_id int(11) unsigned default null,\n" +
                               "    tenant_record_id int(11) unsigned default null,\n" +
                               "    PRIMARY KEY (record_id)\n" +
                               ");");

        for (final String pack : new String[]{"account", "analytics", "beatrix", "subscription", "util", "payment", "invoice", "entitlement", "usage", "meter", "tenant"}) {
            for (final String ddlFile : new String[]{"ddl.sql", "ddl_test.sql"}) {
                final String ddl;
                try {
                    ddl = IOUtils.toString(Resources.getResource("com/ning/billing/" + pack + "/" + ddlFile).openStream());
                } catch (final IllegalArgumentException ignored) {
                    // The test doesn't have this module ddl in the classpath - that's fine
                    continue;
                }
                instance.executeScript(ddl);
            }
        }
        instance.refreshTableNames();
    }

    private static boolean isUsingLocalInstance() {
        return (System.getProperty("com.ning.billing.dbi.test.useLocalDb") != null);
    }
}
