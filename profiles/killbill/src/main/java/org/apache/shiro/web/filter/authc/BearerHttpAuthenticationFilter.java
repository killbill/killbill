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

package org.apache.shiro.web.filter.authc;

import java.util.Locale;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.BearerToken;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter.AUTHENTICATE_HEADER;
import static org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter.AUTHORIZATION_HEADER;

// [PIERRE] COPIED FROM https://github.com/apache/shiro/pull/129 -- to remove once https://github.com/killbill/killbill-oss-parent/issues/36 is closed.

/**
 * Requires the requesting user to be {@link org.apache.shiro.subject.Subject#isAuthenticated() authenticated} for the
 * request to continue, and if they're not, requires the user to login via the HTTP Bearer protocol-specific challenge.
 * Upon successful login, they're allowed to continue on to the requested resource/url.
 * <p/>
 * The {@link #onAccessDenied(ServletRequest, ServletResponse)} method will
 * only be called if the subject making the request is not
 * {@link org.apache.shiro.subject.Subject#isAuthenticated() authenticated}
 *
 * @see <a href="https://tools.ietf.org/html/rfc2617">RFC 2617</a>
 * @see <a href="https://tools.ietf.org/html/rfc6750#section-2.1">OAuth2 Authorization Request Header Field</a>
 * @since 1.5
 */
public class BearerHttpAuthenticationFilter extends AuthenticatingFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerHttpAuthenticationFilter.class);

    private static final String BEARER = "Bearer";

    public BearerHttpAuthenticationFilter() {
    }

    @Override
    protected boolean onAccessDenied(final ServletRequest request, final ServletResponse response) throws Exception {
        boolean loggedIn = false; //false by default or we wouldn't be in this method
        if (isLoginAttempt(request, response)) {
            loggedIn = executeLogin(request, response);
        }
        if (!loggedIn) {
            sendChallenge(request, response);
        }
        return loggedIn;
    }

    private boolean isLoginAttempt(final ServletRequest request, final ServletResponse response) {
        final String authzHeader = getAuthzHeader(request);
        return authzHeader != null && isLoginAttempt(authzHeader);
    }

    private boolean isLoginAttempt(final String authzHeader) {
        //SHIRO-415: use English Locale:
        final String authzScheme = BEARER.toLowerCase(Locale.ENGLISH);
        return authzHeader.toLowerCase(Locale.ENGLISH).startsWith(authzScheme);
    }

    protected boolean sendChallenge(final ServletRequest request, final ServletResponse response) {
        if (log.isDebugEnabled()) {
            log.debug("Authentication required: sending 401 Authentication challenge response.");
        }
        final HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        final String authcHeader = BEARER + " realm=\"application\"";
        httpResponse.setHeader(AUTHENTICATE_HEADER, authcHeader);
        return false;
    }

    protected AuthenticationToken createToken(final ServletRequest request, final ServletResponse response) {
        final String authorizationHeader = getAuthzHeader(request);
        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            // Create an empty authentication token since there is no
            // Authorization header.
            return createBearerToken("", request);
        }

        log.debug("Attempting to execute login with auth header");

        final String[] prinCred = getPrincipalsAndCredentials(authorizationHeader);
        if (prinCred == null || prinCred.length < 1) {
            // Create an authentication token with an empty password,
            // since one hasn't been provided in the request.
            return createBearerToken("", request);
        }

        final String token = prinCred[0] != null ? prinCred[0] : "";
        return createBearerToken(token, request);
    }

    private String getAuthzHeader(final ServletRequest request) {
        final HttpServletRequest httpRequest = WebUtils.toHttp(request);
        return httpRequest.getHeader(AUTHORIZATION_HEADER);
    }

    private String[] getPrincipalsAndCredentials(final String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        final String[] authTokens = authorizationHeader.split(" ");
        if (authTokens.length < 2) {
            return null;
        }
        return new String[]{authTokens[1]};
    }

    private AuthenticationToken createBearerToken(final String token, final ServletRequest request) {
        return new BearerToken(token, request.getRemoteHost());
    }
}
