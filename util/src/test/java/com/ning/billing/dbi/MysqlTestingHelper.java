/*
 * Copyright 2010-2011 Ning, Inc.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.StringMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.util.io.IOUtils;

import com.google.common.io.Resources;
import com.mysql.management.MysqldResource;
import com.mysql.management.MysqldResourceI;

/**
 * Utility class to embed MySQL for testing purposes
 */
public class MysqlTestingHelper {

    public static final String USE_LOCAL_DB_PROP = "com.ning.billing.dbi.test.useLocalDb";

    private static final Logger log = LoggerFactory.getLogger(MysqlTestingHelper.class);

    public static final String DB_NAME = "killbill";
    public static final String USERNAME = "root";
    public static final String PASSWORD = "root";

    // Discover dynamically list of all tables in that database;
    private List<String> allTables;
    private File dbDir;
    private File dataDir;
    private MysqldResource mysqldResource;
    private int port;

    public MysqlTestingHelper() {
        if (isUsingLocalInstance()) {
            port = 3306;
        } else {
            // New socket on any free port
            final ServerSocket socket;
            try {
                socket = new ServerSocket(0);
                port = socket.getLocalPort();
                socket.close();
            } catch (IOException e) {
                Assert.fail();
            }
        }
    }


    public boolean isUsingLocalInstance() {
        return (System.getProperty(USE_LOCAL_DB_PROP) != null);
    }

    public void startMysql() throws IOException {
        if (isUsingLocalInstance()) {
            return;
        }

        dbDir = File.createTempFile("mysqldb", "");
        Assert.assertTrue(dbDir.delete());
        Assert.assertTrue(dbDir.mkdir());

        dataDir = File.createTempFile("mysqldata", "");
        Assert.assertTrue(dataDir.delete());
        Assert.assertTrue(dataDir.mkdir());

        final PrintStream out = new PrintStream(new LoggingOutputStream(log), true);
        mysqldResource = new MysqldResource(dbDir, dataDir, null, out, out);

        final Map<String, String> dbOpts = new HashMap<String, String>();
        dbOpts.put(MysqldResourceI.PORT, Integer.toString(port));
        dbOpts.put(MysqldResourceI.INITIALIZE_USER, "true");
        dbOpts.put(MysqldResourceI.INITIALIZE_PASSWORD, PASSWORD);
        dbOpts.put(MysqldResourceI.INITIALIZE_USER_NAME, USERNAME);
        dbOpts.put("default-time-zone", "+00:00");

        mysqldResource.start("test-mysqld-thread", dbOpts);
        if (!mysqldResource.isRunning()) {
            throw new IllegalStateException("MySQL did not start.");
        } else {
            log.info("MySQL running on port " + mysqldResource.getPort());
            log.info("To connect to it: " + getConnectionString());
        }
    }

    public String getConnectionString() {
        return String.format("mysql -u%s -p%s -P%s -S%s/mysql.sock %s", USERNAME, PASSWORD, port, dataDir, DB_NAME);
    }

    public void cleanupTable(final String table) {

        if (!isUsingLocalInstance() && (mysqldResource == null || !mysqldResource.isRunning())) {
            log.error("Asked to cleanup table " + table + " but MySQL is not running!");
            return;
        }

        log.debug("Deleting table: " + table);
        final IDBI dbi = getDBI();
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("truncate " + table);
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

    public synchronized List<String> fetchAllTables() {

        if (allTables == null) {
            final String dbiString = "jdbc:mysql://localhost:" + port + "/information_schema";
            final IDBI cleanupDbi = new DBI(dbiString, USERNAME, PASSWORD);

            final List<String> tables = cleanupDbi.withHandle(new HandleCallback<List<String>>() {

                @Override
                public List<String> withHandle(final Handle h) throws Exception {
                    return h.createQuery("select table_name from tables where table_schema = :table_schema and table_type = 'BASE TABLE';")
                            .bind("table_schema", DB_NAME)
                            .map(new StringMapper())
                            .list();
                }
            });
            allTables = tables;
        }
        return allTables;
    }


    public void stopMysql() {
        try {
            if (mysqldResource != null) {
                mysqldResource.shutdown();
                deleteRecursive(dataDir);
                deleteRecursive(dbDir);
                log.info("MySQLd stopped");
            }
        } catch (Exception ex) {
            //fail silently
        }
    }

    public IDBI getDBI() {
        final String dbiString = getJdbcConnectionString() + "?createDatabaseIfNotExist=true&allowMultiQueries=true";
        return new DBI(dbiString, USERNAME, PASSWORD);
    }

    public String getJdbcConnectionString() {
        return "jdbc:mysql://localhost:" + port + "/" + DB_NAME;
    }

    public void initDb() throws IOException {
        // We always want the accounts and tenants table
        initDb("drop table if exists accounts; create table accounts(record_id int(11) unsigned not null auto_increment, id char(36) not null, email varchar(128) not null, name varchar(100) not null, first_name_length int not null, is_notified_for_invoices boolean not null, created_date datetime NOT NULL, created_by varchar(50) NOT NULL, updated_date datetime NOT NULL, updated_by varchar(50) NULL, primary key(record_id)) engine=innodb;");
        initDb("drop table if exists tenants; create table tenants(record_id int(11) unsigned not null auto_increment, id char(36) not null, primary key(record_id)) engine=innodb;");

        // We always want the basic tables when we do account_record_id lookups (e.g. for custom fields, tags or junction)
        initDb("drop table if exists bundles; create table bundles(record_id int(11) unsigned not null auto_increment, id char(36) not null, " +
               "account_record_id int(11) unsigned not null, tenant_record_id int(11) unsigned default 0, primary key(record_id)) engine=innodb;");
        initDb("drop table if exists subscriptions; create table subscriptions(record_id int(11) unsigned not null auto_increment, id char(36) not null, " +
               "account_record_id int(11) unsigned not null, tenant_record_id int(11) unsigned default 0, primary key(record_id)) engine=innodb;");

        for (final String pack : new String[]{"account", "analytics", "entitlement", "util", "payment", "invoice", "junction", "tenant"}) {
            final String ddl;
            try {
                ddl = IOUtils.toString(Resources.getResource("com/ning/billing/" + pack + "/ddl.sql").openStream());
            } catch (IllegalArgumentException ignored) {
                // The test doesn't have this module ddl in the classpath - that's fine
                continue;
            }
            initDb(ddl);
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

    public String getDbName() {
        return DB_NAME;
    }

    public static boolean deleteRecursive(final File path) throws FileNotFoundException {
        if (!path.exists()) {
            throw new FileNotFoundException(path.getAbsolutePath());
        }
        boolean ret = true;
        if (path.isDirectory()) {
            for (final File f : path.listFiles()) {
                ret = ret && deleteRecursive(f);
            }
        }
        return ret && path.delete();
    }
}
