/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.server.modules;

import java.io.IOException;

import javax.inject.Inject;

import org.killbill.billing.server.config.DaoConfig;
import org.killbill.billing.server.dao.EmbeddedDBFactory;
import org.killbill.billing.util.io.IOUtils;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.killbill.commons.embeddeddb.EmbeddedDB.DBEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;
import com.google.inject.Provider;

public class EmbeddedDBProvider implements Provider<EmbeddedDB> {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedDBProvider.class);

    private final DaoConfig config;

    @Inject
    public EmbeddedDBProvider(final DaoConfig config) {
        this.config = config;
    }

    @Override
    public EmbeddedDB get() {
        final EmbeddedDB embeddedDB = EmbeddedDBFactory.get(config);

        if (DBEngine.H2.equals(embeddedDB.getDBEngine())) {
            try {
                // Standalone mode?
                initializeEmbeddedDB(embeddedDB);
            } catch (final IOException e) {
                logger.error("Error while initializing H2, opportunistically continuing the startup sequence", e);
            }
        }

        return embeddedDB;
    }

    private void initializeEmbeddedDB(final EmbeddedDB embeddedDB) throws IOException {
        embeddedDB.initialize();
        embeddedDB.start();

        // If the tables have not been created yet, do it, otherwise don't clobber them
        if (!embeddedDB.getAllTables().isEmpty()) {
            return;
        }

        for (final String module : new String[]{"account",
                                                "beatrix",
                                                "entitlement",
                                                "invoice",
                                                "payment",
                                                "subscription",
                                                "tenant",
                                                "usage",
                                                "util"}) {
            final String ddl = IOUtils.toString(Resources.getResource("org/killbill/billing/" + module + "/ddl.sql").openStream());
            embeddedDB.executeScript(ddl);
        }
        embeddedDB.refreshTableNames();
    }
}
