/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.server.listeners;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import net.sf.log4jdbc.sql.jdbcapi.DriverSpy;

public class CleanupListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(CleanupListener.class);

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        // See http://docs.oracle.com/cd/E17952_01/connector-j-relnotes-en/news-5-1-23.html
        try {
            Class.forName("com.mysql.jdbc.AbandonedConnectionCleanupThread");
            com.mysql.jdbc.AbandonedConnectionCleanupThread.shutdown();
        } catch (final InterruptedException e) {
            logger.warn("Unable to shutdown MySQL threads", e);
        } catch (final ClassNotFoundException ignored) {
            // MySQL driver not used
        }

        // See https://mariadb.atlassian.net/browse/CONJ-61
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            // Removed by https://github.com/MariaDB/mariadb-connector-j/commit/ff91ae0bb4f5c49beaba7475b76883b426a51cd4#diff-7d2a758f3b306f512cd12ad68eeb0137
            //org.mariadb.jdbc.Driver.unloadDriver();
        } catch (final ClassNotFoundException ignored) {
            // MariaDB driver not used
        }

        try {
            // Invoke DriverSpy directly if it hasn't been already, as it will statically load drivers
            DriverManager.deregisterDriver(new DriverSpy());
        } catch (final SQLException e) {
            logger.warn("Unable to de-register driver", e);
        }

        // This needs to be last, as drivers above will invoke registerDriver statically
        final Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            try {
                final Driver driver = drivers.nextElement();
                DriverManager.deregisterDriver(driver);
            } catch (final SQLException e) {
                logger.warn("Unable to de-register driver", e);
            }
        }

//         avoid memory leaks: https://logback.qos.ch/manual/jmxConfig.html
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.stop();
    }
}
