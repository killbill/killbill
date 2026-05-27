/*
 * Copyright 2024 The Billing Project, LLC
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

// Regression test for issue #2157: PostgreSQL is the default database engine
// for the Kill Bill reference implementation. The shipped killbill-server.properties
// must resolve to a PostgreSQL JDBC URL so that operators running ./bin/start-server
// (and the OSGi plugin DAO) land on PostgreSQL out of the box.
public class TestDefaultDatabaseConfig extends KillbillTestSuite {

    private static final String SERVER_PROPERTIES = "killbill-server.properties";

    private static final String CORE_DAO_URL = "org.killbill.dao.url";
    private static final String CORE_DAO_USER = "org.killbill.dao.user";
    private static final String CORE_DAO_PASSWORD = "org.killbill.dao.password";

    private static final String OSGI_DAO_URL = "org.killbill.billing.osgi.dao.url";

    @Test(groups = "fast")
    public void testDefaultDaoUrlIsPostgresql() throws Exception {
        final Properties properties = loadServerProperties();

        final String coreUrl = properties.getProperty(CORE_DAO_URL);
        Assert.assertNotNull(coreUrl, CORE_DAO_URL + " must be set in " + SERVER_PROPERTIES);
        Assert.assertTrue(coreUrl.startsWith("jdbc:postgresql:"),
                          "Default " + CORE_DAO_URL + " must be a PostgreSQL JDBC URL, got: " + coreUrl);

        final String osgiUrl = properties.getProperty(OSGI_DAO_URL);
        Assert.assertNotNull(osgiUrl, OSGI_DAO_URL + " must be set in " + SERVER_PROPERTIES);
        Assert.assertTrue(osgiUrl.startsWith("jdbc:postgresql:"),
                          "Default " + OSGI_DAO_URL + " must be a PostgreSQL JDBC URL, got: " + osgiUrl);
    }

    @Test(groups = "fast")
    public void testEmbeddedDBFactoryResolvesToPostgresql() throws Exception {
        final Properties properties = loadServerProperties();
        final DaoConfig daoConfig = new AugmentedConfigurationObjectFactory(properties).build(DaoConfig.class);

        final EmbeddedDB embeddedDB = EmbeddedDBFactory.get(daoConfig);
        Assert.assertEquals(embeddedDB.getDBEngine(), DBEngine.POSTGRESQL,
                            "EmbeddedDBFactory must resolve the default killbill-server.properties to PostgreSQL");
        Assert.assertEquals(embeddedDB.getDatabaseName(), "killbill");
    }

    private Properties loadServerProperties() throws Exception {
        final Properties properties = new Properties();
        try (final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(SERVER_PROPERTIES)) {
            Assert.assertNotNull(stream, SERVER_PROPERTIES + " must be available on the classpath");
            properties.load(stream);
        }
        return properties;
    }
}
