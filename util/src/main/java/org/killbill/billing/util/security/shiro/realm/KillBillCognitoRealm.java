/*
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

package org.killbill.billing.util.security.shiro.realm;

import java.io.Serializable;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.HostAuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.killbill.billing.platform.glue.KillBillPlatformModuleBase;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.commons.utils.Strings;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Clock;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.security.SignatureException;

/**
 * Validates Cognito ID tokens sent as {@code Authorization: Bearer <id_token>}, and grants the
 * permissions of the role found in the token, as defined in {@code roles_permissions}.
 * <p>
 * Enabled with {@code -Dkillbill.server.cognito=true}.
 * <ul>
 *     <li>Only Bearer tokens are handled: Basic credentials are left to the other realms.</li>
 *     <li>The token must be a Cognito ID token ({@code token_use=id}) signed by the pool's keys, with the configured
 *     issuer, an accepted audience (app client id) and an expiration.</li>
 *     <li>The principal is {@code cognito:<claim>}. Authorization is the union of all realms, so the JDBC realm grants
 *     nothing to the subjects of this realm, and local usernames starting with {@code cognito:} are reserved.</li>
 *     <li>The role is carried in the principal collection (doGetAuthorizationInfo does not see the token). A token
 *     without a role is rejected: most read APIs only require authentication, so authenticating a token without a role
 *     would still give read access.</li>
 * </ul>
 */
public class KillBillCognitoRealm extends AuthorizingRealm {

    public static final String PRINCIPAL_PREFIX = "cognito:";

    private static final Logger log = LoggerFactory.getLogger(KillBillCognitoRealm.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String USER_AGENT = "KillBill/1.0";
    private static final String PERMISSIONS_QUERY = "select permission from roles_permissions where role_name = ? and is_active = TRUE";

    private final SecurityConfig securityConfig;
    private final DataSource dataSource;
    private final HttpClient httpClient;
    private final JwtParser jwtParser;
    private final String issuer;
    private final Set<String> audiences;
    private final long jwksMinRefreshIntervalMillis;
    private final long jwksMaxAgeMillis;
    private final long readTimeoutMillis;
    private final org.killbill.clock.Clock clock;

    // Signing keys are read without locking; only one thread downloads them at a time
    private final ReentrantLock jwksRefreshLock = new ReentrantLock();
    private volatile Map<String, PublicKey> keys = Collections.emptyMap();
    private volatile long keysFetchedMillis = 0;
    // Guarded by jwksRefreshLock
    private long nextJwksAttemptMillis = 0;

    @Inject
    public KillBillCognitoRealm(final SecurityConfig securityConfig,
                                final org.killbill.clock.Clock clock,
                                @Named(KillBillPlatformModuleBase.SHIRO_DATA_SOURCE_ID) final DataSource dataSource) {
        this.securityConfig = securityConfig;
        this.dataSource = dataSource;
        this.clock = clock;

        this.issuer = stripTrailingSlash(securityConfig.getShiroCognitoIssuer());
        if (Strings.isNullOrEmpty(issuer)) {
            throw new IllegalStateException("org.killbill.security.cognito.issuer must be set when the Cognito realm is enabled");
        }
        this.audiences = parseAudiences(securityConfig.getShiroCognitoAudiences());
        if (audiences.isEmpty()) {
            throw new IllegalStateException("org.killbill.security.cognito.audiences must be set when the Cognito realm is enabled");
        }
        this.jwksMinRefreshIntervalMillis = securityConfig.getShiroCognitoJwksMinRefreshInterval().getMillis();
        this.jwksMaxAgeMillis = securityConfig.getShiroCognitoJwksMaxAge().getMillis();
        this.readTimeoutMillis = securityConfig.getShiroCognitoReadTimeout().getMillis();

        this.httpClient = HttpClient.newBuilder()
                                    .connectTimeout(Duration.ofMillis(securityConfig.getShiroCognitoConnectTimeout().getMillis()))
                                    .build();

        this.jwtParser = Jwts.parserBuilder()
                             .requireIssuer(issuer)
                             .setClock(new Clock() {
                                 @Override
                                 public Date now() {
                                     return clock.getUTCNow().toDate();
                                 }
                             })
                             .setAllowedClockSkewSeconds(securityConfig.getShiroCognitoAllowedClockSkew().getMillis() / 1000)
                             .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                                 @Override
                                 public Key resolveSigningKey(final JwsHeader header, final Claims claims) {
                                     return getSigningKey(header.getKeyId());
                                 }
                             })
                             .build();
    }

    /**
     * Whether the subject was authenticated by this realm. The other realms must grant nothing to such subjects: their
     * principal is not a user of theirs, even if a user with the same name exists.
     */
    public static boolean isCognitoSubject(final PrincipalCollection principals) {
        return principals != null && principals.oneByType(CognitoRole.class) != null;
    }

    @Override
    public boolean supports(final AuthenticationToken token) {
        // BearerToken lives in killbill-server and is not visible here: accept any host token but Basic credentials
        return token instanceof HostAuthenticationToken &&
               !(token instanceof UsernamePasswordToken) &&
               token.getPrincipal() instanceof String;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token) throws AuthenticationException {
        final Claims claims = verifyJWT((String) token.getPrincipal());

        final Object principalValue = claims.get(securityConfig.getShiroCognitoPrincipalClaim());
        if (!(principalValue instanceof String) || Strings.isNullOrEmpty((String) principalValue)) {
            throw new AuthenticationException("Claim " + securityConfig.getShiroCognitoPrincipalClaim() + " is missing from the ID token");
        }
        final Object roleValue = claims.get(securityConfig.getShiroCognitoRoleClaim());
        if (!(roleValue instanceof String) || Strings.isNullOrEmpty((String) roleValue)) {
            throw new AuthenticationException("Claim " + securityConfig.getShiroCognitoRoleClaim() + " is missing from the ID token");
        }

        final SimplePrincipalCollection principals = new SimplePrincipalCollection();
        principals.add(PRINCIPAL_PREFIX + principalValue, getName());
        principals.add(new CognitoRole((String) roleValue), getName());
        return new SimpleAuthenticationInfo(principals, token.getCredentials());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(final PrincipalCollection principals) {
        final Collection<?> ours = principals.fromRealm(getName());
        if (ours == null || ours.isEmpty()) {
            // Subject authenticated by another realm
            return null;
        }
        final SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();
        for (final Object principal : ours) {
            if (principal instanceof CognitoRole) {
                final String role = ((CognitoRole) principal).getName();
                final Set<String> permissions = findPermissions(role);
                if (permissions.isEmpty()) {
                    log.info("Cognito role {} has no permission in roles_permissions", role);
                }
                authorizationInfo.setStringPermissions(permissions);
            }
        }
        return authorizationInfo;
    }

    @VisibleForTesting
    Claims verifyJWT(final String token) throws AuthenticationException {
        if (Strings.isNullOrEmpty(token)) {
            throw new AuthenticationException("ID token is required but missing");
        }

        final Claims claims;
        try {
            claims = jwtParser.parseClaimsJws(token).getBody();
        } catch (final JwtException | IllegalArgumentException e) {
            log.warn("Invalid Cognito JWT: {}", e.getMessage());
            throw new AuthenticationException("ID token could not be verified", e);
        }

        if (claims.getExpiration() == null) {
            throw new AuthenticationException("ID token has no expiration");
        }
        if (!"id".equals(claims.get("token_use"))) {
            throw new AuthenticationException("Token is not a Cognito ID token");
        }
        if (Strings.isNullOrEmpty(claims.getSubject())) {
            throw new AuthenticationException("ID token has no subject");
        }
        final Object aud = claims.get(Claims.AUDIENCE);
        if (!(aud instanceof String) || !audiences.contains(aud)) {
            throw new AuthenticationException("ID token audience is not accepted");
        }
        return claims;
    }

    @VisibleForTesting
    protected Set<String> findPermissions(final String role) {
        final Set<String> permissions = new HashSet<>();
        try (final Connection connection = dataSource.getConnection();
             final PreparedStatement statement = connection.prepareStatement(PERMISSIONS_QUERY)) {
            statement.setString(1, role);
            try (final ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    permissions.add(resultSet.getString(1));
                }
            }
        } catch (final SQLException e) {
            throw new AuthorizationException("Unable to retrieve permissions for role " + role, e);
        }
        return permissions;
    }

    private Key getSigningKey(final String keyId) {
        if (keyId == null) {
            throw new SignatureException("Key ID is required");
        }
        final long now = clock.getUTCNow().getMillis();
        PublicKey key = keys.get(keyId);
        // Unknown kid (first call or key rotation), or keys too old (a key removed from the pool must stop being trusted)
        if (key == null || now - keysFetchedMillis >= jwksMaxAgeMillis) {
            // A request without a key waits for a download in progress (bounded by the read timeout), one with a key does not
            refreshJwks(now, key == null);
            key = keys.get(keyId);
        }
        if (key == null) {
            throw new SignatureException("Unknown signing key ID");
        }
        return key;
    }

    /**
     * Downloads the signing keys, at most once per jwksMinRefreshInterval (failed attempts included). If the download
     * fails, the current keys are kept until the next attempt.
     */
    private void refreshJwks(final long now, final boolean waitForDownloadInProgress) {
        if (waitForDownloadInProgress) {
            jwksRefreshLock.lock();
        } else if (!jwksRefreshLock.tryLock()) {
            return;
        }
        try {
            if (now < nextJwksAttemptMillis) {
                return;
            }
            nextJwksAttemptMillis = now + jwksMinRefreshIntervalMillis;
            try {
                keys = fetchJwks();
                keysFetchedMillis = now;
            } catch (final SignatureException e) {
                log.warn("Unable to refresh the Cognito signing keys, keeping the {} current key(s): {}", keys.size(), e.getMessage());
            }
        } finally {
            jwksRefreshLock.unlock();
        }
    }

    @VisibleForTesting
    protected Map<String, PublicKey> fetchJwks() {
        final HttpRequest request = HttpRequest.newBuilder()
                                               .uri(URI.create(issuer + "/.well-known/jwks.json"))
                                               .header("User-Agent", USER_AGENT)
                                               .timeout(Duration.ofMillis(readTimeoutMillis))
                                               .build();
        // The body is read in full (so the connection is always released), and the read timeout bounds the whole
        // exchange: the request timeout alone stops at the response headers
        final CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        final Map<String, List<Map<String, Object>>> jwks;
        try {
            final HttpResponse<byte[]> response = future.get(readTimeoutMillis, TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                throw new SignatureException("Unable to download the Cognito signing keys: HTTP " + response.statusCode());
            }
            jwks = mapper.readValue(response.body(), new TypeReference<Map<String, List<Map<String, Object>>>>() {});
        } catch (final SignatureException e) {
            throw e;
        } catch (final TimeoutException e) {
            future.cancel(true);
            throw new SignatureException("Timed out downloading the Cognito signing keys", e);
        } catch (final InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new SignatureException("Interrupted while downloading the Cognito signing keys", e);
        } catch (final ExecutionException e) {
            throw new SignatureException("Unable to download the Cognito signing keys: " + e.getCause(), e.getCause());
        } catch (final Exception e) {
            throw new SignatureException("Unable to download the Cognito signing keys: " + e, e);
        }
        return parseJwks(jwks);
    }

    @VisibleForTesting
    static Map<String, PublicKey> parseJwks(final Map<String, List<Map<String, Object>>> jwks) {
        final Map<String, PublicKey> result = new HashMap<>();
        if (jwks == null || jwks.get("keys") == null) {
            return result;
        }
        for (final Map<String, Object> jwk : jwks.get("keys")) {
            if (!(jwk.get("kid") instanceof String) || !"RSA".equals(jwk.get("kty")) ||
                !(jwk.get("n") instanceof String) || !(jwk.get("e") instanceof String)) {
                continue;
            }
            try {
                final BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("n")));
                final BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("e")));
                result.put((String) jwk.get("kid"), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent)));
            } catch (final Exception e) {
                log.warn("Ignoring invalid Cognito signing key {}", jwk.get("kid"), e);
            }
        }
        return result;
    }

    private static Set<String> parseAudiences(final String audiences) {
        if (audiences == null) {
            return Collections.emptySet();
        }
        return Stream.of(audiences.split(","))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toUnmodifiableSet());
    }

    private static String stripTrailingSlash(final String value) {
        if (value != null && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * Role from the ID token, kept in the principal collection. Serializable and with value semantics: it is part of
     * the session and of the authorization cache key, so a token with a new role gets a fresh lookup.
     */
    public static final class CognitoRole implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String name;

        public CognitoRole(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(final Object o) {
            return o instanceof CognitoRole && Objects.equals(name, ((CognitoRole) o).name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public String toString() {
            return "CognitoRole{" + name + '}';
        }
    }
}
