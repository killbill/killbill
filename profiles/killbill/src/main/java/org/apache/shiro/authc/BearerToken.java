/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.shiro.authc;

// [PIERRE] COPIED FROM https://github.com/apache/shiro/pull/129 -- to remove once https://github.com/killbill/killbill-oss-parent/issues/36 is closed.

/**
 * A {@link AuthenticationToken} that contains an a Bearer token or API key, typically received via an HTTP {@code Authorization} header. This
 * class also implements the {@link org.apache.shiro.authc.HostAuthenticationToken HostAuthenticationToken} interface to retain the host name
 * or IP address location from where the authentication attempt is occurring.
 *
 * @see <a href="https://tools.ietf.org/html/rfc2617">RFC 2617</a>
 * @see <a href="https://tools.ietf.org/html/rfc6750#section-2.1">OAuth2 Authorization Request Header Field</a>
 * @since 1.5
 */
public class BearerToken implements HostAuthenticationToken {

    private final String token;

    /**
     * The location from where the login attempt occurs, or <code>null</code> if not known or explicitly
     * omitted.
     */
    private final String host;

    public BearerToken(final String token) {
        this(token, null);
    }

    public BearerToken(final String token, final String host) {
        this.token = token;
        this.host = host;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    public String getToken() {
        return token;
    }
}