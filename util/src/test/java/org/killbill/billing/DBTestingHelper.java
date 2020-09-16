/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLNonTransientConnectionException;
import java.util.Enumeration;

import javax.sql.DataSource;

import org.killbill.billing.platform.test.PlatformDBTestingHelper;
import org.killbill.billing.util.glue.IDBISetup;
import org.killbill.billing.util.io.IOUtils;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.killbill.commons.jdbi.guice.DBIProvider;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.ResultSetMapperFactory;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

public class DBTestingHelper extends PlatformDBTestingHelper {

    private static final Logger log = LoggerFactory.getLogger(DBTestingHelper.class);

    private static DBTestingHelper dbTestingHelper = null;

    private DBI dbi;

    public static synchronized DBTestingHelper get() {
        if (dbTestingHelper == null) {
            dbTestingHelper = new DBTestingHelper();
        }
        return dbTestingHelper;
    }

    private DBTestingHelper() {
        super();
    }

    @Override
    public synchronized IDBI getDBI() {
        if (dbi == null) {
            final RetryableDataSource retryableDataSource = new RetryableDataSource(getDataSource());
            dbi = (DBI) new DBIProvider(null, retryableDataSource, null).get();

            // Register KB specific mappers
            for (final ResultSetMapperFactory resultSetMapperFactory : IDBISetup.mapperFactoriesToRegister()) {
                dbi.registerMapper(resultSetMapperFactory);
            }
            for (final ResultSetMapper resultSetMapper : IDBISetup.mappersToRegister()) {
                dbi.registerMapper(resultSetMapper);
            }
        }
        return dbi;
    }

    protected synchronized void executePostStartupScripts() throws IOException {

        final EmbeddedDB instance = getInstance();
        final String databaseSpecificDDL = "org/killbill/billing/util/" + "ddl-" + instance.getDBEngine().name().toLowerCase() + ".sql";
        installDDLSilently(databaseSpecificDDL);

        for (final String pack : new String[]{"util", "catalog", "account", "analytics", "beatrix", "subscription", "payment", "invoice", "entitlement", "usage", "meter", "tenant"}) {
            // Test DDL first as the main DDL takes precedence in case of dups
            for (final String ddlFile : new String[]{"ddl_test.sql","ddl.sql"}) {
                final String resourceName = "org/killbill/billing/" + pack + "/" + ddlFile;
                installDDLSilently(resourceName);
            }
        }
    }

    private void installDDLSilently(final String resourceName) throws IOException {
        final ClassLoader classLoader = MoreObjects.firstNonNull(Thread.currentThread().getContextClassLoader(), DBTestingHelper.class.getClassLoader());
        final Enumeration<URL> resources = classLoader.getResources(resourceName);
        while (resources.hasMoreElements()) {
            final URL inputStream = resources.nextElement();

            final String ddl;
            try {
                ddl = IOUtils.toString(inputStream.openStream());
                log.info("Installing DDL: {}", inputStream.getPath());
                getInstance().executeScript(ddl);
            } catch (final Exception ignored) {
                log.warn("Ignoring exception", ignored);
            }
        }
    }

    // DataSource which will retry recreating a connection once in case of a connection exception.
    // This is useful for transient network errors in tests when using a separate database (e.g. Docker container),
    // as we don't use a connection pool.
    private static final class RetryableDataSource implements DataSource {

        private static final Logger logger = LoggerFactory.getLogger(RetryableDataSource.class);

        private final DataSource delegate;

        private RetryableDataSource(final DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            try {
                return delegate.getConnection();
            } catch (final SQLNonTransientConnectionException| SQLInvalidAuthorizationSpecException e) { // For some reason, we now get transient SQLInvalidAuthorizationSpecException errors
                logger.debug("Unable to retrieve connection, attempting to retry", e);
                return delegate.getConnection();
            }
        }

        @Override
        public Connection getConnection(final String username, final String password) throws SQLException {
            try {
                return delegate.getConnection(username, password);
            } catch (final SQLNonTransientConnectionException| SQLInvalidAuthorizationSpecException e) { // For some reason, we now get transient SQLInvalidAuthorizationSpecException errors
                logger.debug("Unable to retrieve connection, attempting to retry", e);
                return delegate.getConnection(username, password);
            }
        }

        @Override
        public <T> T unwrap(final Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(final PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(final int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        //@Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("javax.sql.DataSource.getParentLogger() is not currently supported by " + this.getClass().getName());
        }
    }
}
