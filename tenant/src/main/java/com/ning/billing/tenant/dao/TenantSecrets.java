/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.tenant.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.util.ByteSource;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.util.dao.MapperBase;

/**
 * Not exposed in the APIs - mainly for testing
 */
public final class TenantSecrets {

    private final String apiKey;
    // Encrypted secret
    private final String apiSecret;
    private final String apiSalt;

   public TenantSecrets(final String apiKey, final String apiSecret, final String apiSalt) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.apiSalt = apiSalt;
    }

    public AuthenticationInfo toAuthenticationInfo() {
        final SimpleAuthenticationInfo authenticationInfo = new SimpleAuthenticationInfo(apiKey, apiSecret.toCharArray(), getClass().getSimpleName());
        authenticationInfo.setCredentialsSalt(ByteSource.Util.bytes(Base64.decode(apiSalt)));
        return authenticationInfo;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public String getApiSalt() {
        return apiSalt;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TenantSecrets");
        sb.append("{apiKey='").append(apiKey).append('\'');
        // Don't print the secret nor salt
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final TenantSecrets that = (TenantSecrets) o;

        if (apiKey != null ? !apiKey.equals(that.apiKey) : that.apiKey != null) {
            return false;
        }
        if (apiSalt != null ? !apiSalt.equals(that.apiSalt) : that.apiSalt != null) {
            return false;
        }
        if (apiSecret != null ? !apiSecret.equals(that.apiSecret) : that.apiSecret != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = apiKey != null ? apiKey.hashCode() : 0;
        result = 31 * result + (apiSecret != null ? apiSecret.hashCode() : 0);
        result = 31 * result + (apiSalt != null ? apiSalt.hashCode() : 0);
        return result;
    }
}
