/*
 * Copyright 2020-2026 Equinix, Inc
 * Copyright 2014-2026 The Billing Project, LLC
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

package org.killbill.billing.server.dao;

import java.io.InputStream;
import java.util.Properties;

import org.killbill.billing.KillbillTestSuite;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.killbill.commons.embeddeddb.EmbeddedDB.DBEngine;
import org.killbill.commons.jdbi.guice.DaoConfig;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Verifies that the reference server profile ships PostgreSQL as the default
 * database engine (issue #2157).
 */
public class TestDefaultDatabaseConfig extends KillbillTestSuite {

    private static final String SERVER_PROPERTIES = "/killbill-server.properties";
    private static final String DAO_URL_KEY = "org.killbill.dao.url";
    private static final String OSGI_DAO_URL_KEY = "org.killbill.billing.osgi.dao.url";

    @Test(groups = "fast")
    public void testReferenceProfileDefaultsToPostgreSQL() throws Exception {
        final Properties properties = loadServerProperties();

        final String daoUrl = properties.getProperty(DAO_URL_KEY);
        Assert.assertNotNull(daoUrl, DAO_URL_KEY + " must be set in killbill-server.properties");
        Assert.assertTrue(daoUrl.startsWith("jdbc:postgresql:"),
                          "Expected default DAO JDBC URL to be PostgreSQL, was: " + daoUrl);

        final String osgiDaoUrl = properties.getProperty(OSGI_DAO_URL_KEY);
        Assert.assertNotNull(osgiDaoUrl, OSGI_DAO_URL_KEY + " must be set in killbill-server.properties");
        Assert.assertTrue(osgiDaoUrl.startsWith("jdbc:postgresql:"),
                          "Expected default OSGI DAO JDBC URL to be PostgreSQL, was: " + osgiDaoUrl);
    }

    @Test(groups = "fast")
    public void testDefaultUrlResolvesToPostgreSQLEngine() throws Exception {
        final Properties properties = loadServerProperties();
        final DaoConfig daoConfig = new AugmentedConfigurationObjectFactory(properties).build(DaoConfig.class);

        final EmbeddedDB embeddedDB = EmbeddedDBFactory.get(daoConfig);
        Assert.assertEquals(embeddedDB.getDBEngine(), DBEngine.POSTGRESQL,
                            "Default killbill-server.properties should resolve to the PostgreSQL engine");
    }

    private Properties loadServerProperties() throws Exception {
        final Properties properties = new Properties();
        try (final InputStream in = getClass().getResourceAsStream(SERVER_PROPERTIES)) {
            Assert.assertNotNull(in, "Could not load " + SERVER_PROPERTIES + " from the classpath");
            properties.load(in);
        }
        return properties;
    }
}
