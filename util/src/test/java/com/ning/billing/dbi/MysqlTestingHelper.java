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
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.StringMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.mysql.management.MysqldResource;
import com.mysql.management.MysqldResourceI;

/**
 * Utility class to embed MySQL for testing purposes
 */
public class MysqlTestingHelper
{

    public static final String USE_LOCAL_DB_PROP = "com.ning.billing.dbi.test.useLocalDb";

    private static final Logger log = LoggerFactory.getLogger(MysqlTestingHelper.class);

    private static final String DB_NAME = "killbill";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";

    // Discover dynamically list of all tables in that database;
    private List<String> allTables;    
    private File dbDir;
    private MysqldResource mysqldResource;
    private int port;

    public MysqlTestingHelper()
    {
        if (isUsingLocalInstance()) {
            port = 3306;
        } else {
            // New socket on any free port
            final ServerSocket socket;
            try {
                socket = new ServerSocket(0);
                port = socket.getLocalPort();
                socket.close();
            }
            catch (IOException e) {
                Assert.fail();
            }
        }
    }


    public boolean isUsingLocalInstance() {
        return (System.getProperty(USE_LOCAL_DB_PROP) != null);
    }

    public void startMysql() throws IOException
    {
        if (isUsingLocalInstance()) {
            return;
        }

        dbDir = File.createTempFile("mysql", "");
        dbDir.delete();
        dbDir.mkdir();
        mysqldResource = new MysqldResource(dbDir);

        final Map<String, String> dbOpts = new HashMap<String, String>();
        dbOpts.put(MysqldResourceI.PORT, Integer.toString(port));
        dbOpts.put(MysqldResourceI.INITIALIZE_USER, "true");
        dbOpts.put(MysqldResourceI.INITIALIZE_PASSWORD, PASSWORD);
        dbOpts.put(MysqldResourceI.INITIALIZE_USER_NAME, USERNAME);
        dbOpts.put("default-time-zone", "+00:00");

        mysqldResource.start("test-mysqld-thread", dbOpts);
        if (!mysqldResource.isRunning()) {
            throw new IllegalStateException("MySQL did not start.");
        }
        else {
            log.info("MySQL running on port " + mysqldResource.getPort());
        }
    }

    public void cleanupTable(final String table)
    {

        if (!isUsingLocalInstance() && (mysqldResource == null || !mysqldResource.isRunning())) {
            log.error("Asked to cleanup table " + table + " but MySQL is not running!");
            return;
        }

        log.info("Deleting table: " + table);
        final IDBI dbi = getDBI();
        dbi.withHandle(new HandleCallback<Void>()
        {
            @Override
            public Void withHandle(final Handle handle) throws Exception
            {
                handle.execute("truncate " + table);
                return null;
            }
        });
    }
    
    public void cleanupAllTables() {
    	final List<String> tablesToCleanup = fetchAllTables();
    	for (String tableName : tablesToCleanup) {
    		cleanupTable(tableName);
    	}
    }

    public synchronized List<String> fetchAllTables() {

    	if (allTables == null) {
    		final String dbiString = "jdbc:mysql://localhost:" + port + "/information_schema";
    		IDBI cleanupDbi = new DBI(dbiString, USERNAME, PASSWORD);

    		final List<String> tables=  cleanupDbi.withHandle(new HandleCallback<List<String>>() {

    			@Override
    			public List<String> withHandle(Handle h) throws Exception {
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

    
    public void stopMysql()
    {
        if (mysqldResource != null) {
            mysqldResource.shutdown();
            FileUtils.deleteQuietly(dbDir);
            log.info("MySQLd stopped");
        }
    }

    public IDBI getDBI()
    {
        final String dbiString = "jdbc:mysql://localhost:" + port + "/" + DB_NAME + "?createDatabaseIfNotExist=true&allowMultiQueries=true";
        return new DBI(dbiString, USERNAME, PASSWORD);
    }

    public void initDb(final String ddl) throws IOException
    {
        if (isUsingLocalInstance()) {
            return;
        }
        final IDBI dbi = getDBI();
        dbi.withHandle(new HandleCallback<Void>()
        {
            @Override
            public Void withHandle(final Handle handle) throws Exception
            {
                log.info("Executing DDL script: " + ddl);
                handle.createScript(ddl).execute();
                return null;
            }
        });
    }

    public String getDbName() {
        return DB_NAME;
    }
}
