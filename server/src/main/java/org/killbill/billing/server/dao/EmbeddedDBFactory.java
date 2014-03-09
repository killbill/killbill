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

package org.killbill.billing.server.dao;

import java.net.URI;

import org.killbill.billing.server.config.DaoConfig;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.killbill.commons.embeddeddb.GenericStandaloneDB;
import org.killbill.commons.embeddeddb.h2.H2EmbeddedDB;
import org.killbill.commons.embeddeddb.mysql.MySQLStandaloneDB;

public class EmbeddedDBFactory {

    private EmbeddedDBFactory() { }

    public static EmbeddedDB get(final DaoConfig config) {
        final URI uri = URI.create(config.getJdbcUrl().substring(5));

        final String databaseName;
        final String schemeLocation;
        if (uri.getPath() != null) {
            schemeLocation = null;
            databaseName = uri.getPath().split("/")[1].split(";")[0];
        } else if (uri.getSchemeSpecificPart() != null) {
            final String[] schemeParts = uri.getSchemeSpecificPart().split(":");
            schemeLocation = schemeParts[0];
            databaseName = schemeParts[1].split(";")[0];
        } else {
            schemeLocation = null;
            databaseName = null;
        }

        if ("mysql".equals(uri.getScheme())) {
            return new MySQLStandaloneDB(databaseName, config.getUsername(), config.getPassword(), config.getJdbcUrl());
        } else if ("h2".equals(uri.getScheme()) && ("mem".equals(schemeLocation) || "file".equals(schemeLocation))) {
            return new H2EmbeddedDB(databaseName, config.getUsername(), config.getPassword(), config.getJdbcUrl());
        } else {
            return new GenericStandaloneDB(databaseName, config.getUsername(), config.getPassword(), config.getJdbcUrl());
        }
    }
}
