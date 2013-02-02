/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.server.security;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.util.ByteSource;
import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.tenant.security.KillbillCredentialsMatcher;
import com.ning.jetty.jdbi.config.DaoConfig;

import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;

/**
 * @see {shiro.ini}
 */
public class KillbillJdbcRealm extends JdbcRealm {

    private static final String KILLBILL_AUTHENTICATION_QUERY = "select api_secret, api_salt from tenants where api_key = ?";

    public KillbillJdbcRealm() {
        super();
        configureSecurity();
        configureQueries();
        configureDataSource();
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token) throws AuthenticationException {
        final SimpleAuthenticationInfo authenticationInfo = (SimpleAuthenticationInfo) super.doGetAuthenticationInfo(token);

        // We store the salt bytes in Base64 (because the JdbcRealm retrieves it as a String)
        final ByteSource base64Salt = authenticationInfo.getCredentialsSalt();
        authenticationInfo.setCredentialsSalt(ByteSource.Util.bytes(Base64.decode(base64Salt.getBytes())));

        return authenticationInfo;
    }

    private void configureSecurity() {
        setSaltStyle(SaltStyle.COLUMN);
        setCredentialsMatcher(KillbillCredentialsMatcher.getCredentialsMatcher());
    }

    private void configureQueries() {
        setAuthenticationQuery(KILLBILL_AUTHENTICATION_QUERY);
    }

    private void configureDataSource() {
        // This class is initialized by Shiro, not Guice - we need to retrieve the config manually
        final DaoConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DaoConfig.class);

        final BoneCPConfig dbConfig = new BoneCPConfig();
        dbConfig.setJdbcUrl(config.getJdbcUrl());
        dbConfig.setUsername(config.getUsername());
        dbConfig.setPassword(config.getPassword());
        dbConfig.setMinConnectionsPerPartition(config.getMinIdle());
        dbConfig.setMaxConnectionsPerPartition(config.getMaxActive());
        dbConfig.setConnectionTimeout(config.getConnectionTimeout().getPeriod(), config.getConnectionTimeout().getUnit());
        dbConfig.setIdleMaxAge(config.getIdleMaxAge().getPeriod(), config.getIdleMaxAge().getUnit());
        dbConfig.setMaxConnectionAge(config.getMaxConnectionAge().getPeriod(), config.getMaxConnectionAge().getUnit());
        dbConfig.setIdleConnectionTestPeriod(config.getIdleConnectionTestPeriod().getPeriod(), config.getIdleConnectionTestPeriod().getUnit());
        dbConfig.setPartitionCount(1);
        dbConfig.setDefaultTransactionIsolation("READ_COMMITTED");
        dbConfig.setDisableJMX(false);

        setDataSource(new BoneCPDataSource(dbConfig));
    }
}
