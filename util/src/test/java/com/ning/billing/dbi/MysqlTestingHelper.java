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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.mysql.management.MysqldResource;
import com.mysql.management.MysqldResourceI;

/**
 * Utility class to embed MySQL for testing purposes
 */
public class MysqlTestingHelper extends DBTestingHelper {

    public static final String USE_LOCAL_DB_PROP = "com.ning.billing.dbi.test.useLocalDb";

    private static final Logger log = LoggerFactory.getLogger(MysqlTestingHelper.class);

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
            } catch (final IOException e) {
                Assert.fail();
            }
        }
    }

    @Override
    public boolean isUsingLocalInstance() {
        return (System.getProperty(USE_LOCAL_DB_PROP) != null);
    }

    @Override
    public String getConnectionString() {
        return String.format("mysql -u%s -p%s -P%s -S%s/mysql.sock %s", USERNAME, PASSWORD, port, dataDir, DB_NAME);
    }

    @Override
    public String getJdbcConnectionString() {
        return "jdbc:mysql://localhost:" + port + "/" + DB_NAME + "?createDatabaseIfNotExist=true&allowMultiQueries=true";
    }

    @Override
    public String getInformationSchemaJdbcConnectionString() {
        return "jdbc:mysql://localhost:" + port + "/information_schema";
    }

    public void start() throws IOException {
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

    public void stop() {
        try {
            if (mysqldResource != null) {
                mysqldResource.shutdown();
                deleteRecursive(dataDir);
                deleteRecursive(dbDir);
                log.info("MySQLd stopped");
            }
        } catch (final Exception ex) {
            //fail silently
        }
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
