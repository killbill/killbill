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

import com.mysql.management.MysqldResource;
import com.mysql.management.MysqldResourceI;
import org.apache.commons.io.FileUtils;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to embed MySQL for testing purposes
 */
public class MysqlTestingHelper
{
    private static final Logger log = LoggerFactory.getLogger(MysqlTestingHelper.class);

    private static final String DB_NAME = "test_killbill";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "";

    private File dbDir;
    private MysqldResource mysqldResource;
    private int port = 0;

    public MysqlTestingHelper()
    {
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

    public void startMysql() throws IOException
    {
        dbDir = File.createTempFile("mysql", "");
        dbDir.delete();
        dbDir.mkdir();
        mysqldResource = new MysqldResource(dbDir);

        final Map<String, String> dbOpts = new HashMap<String, String>();
        dbOpts.put(MysqldResourceI.PORT, Integer.toString(port));
        dbOpts.put(MysqldResourceI.INITIALIZE_USER, "true");
        dbOpts.put(MysqldResourceI.INITIALIZE_USER_NAME, USERNAME);
        dbOpts.put(MysqldResourceI.INITIALIZE_PASSWORD, PASSWORD);

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
        if (mysqldResource == null || !mysqldResource.isRunning()) {
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

    public void stopMysql()
    {
        if (mysqldResource != null) {
            mysqldResource.shutdown();
            FileUtils.deleteQuietly(dbDir);
            log.info("MySQLd stopped");
        }
    }

    public DBI getDBI()
    {
        final String dbiString = "jdbc:mysql://localhost:" + port + "/" + DB_NAME + "?createDatabaseIfNotExist=true";
        return new DBI(dbiString, USERNAME, PASSWORD);
    }

    public void initDb(final String ddl) throws IOException
    {
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
}
