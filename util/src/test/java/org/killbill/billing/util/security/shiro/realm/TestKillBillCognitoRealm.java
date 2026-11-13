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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.HostAuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.ModularRealmAuthorizer;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.cache.MemoryConstrainedCacheManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.security.api.DefaultSecurityApi;
import org.killbill.billing.util.security.shiro.dao.UserDao;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

public class TestKillBillCognitoRealm extends UtilTestSuiteNoDB {

    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST";
    private static final String AUD_PASSWORD = "password-client";
    private static final String AUD_SSO = "sso-client";
    private static final String KID = "kid-1";

    private KeyPair keyPair;
    private KeyPair otherKeyPair;
    private AtomicInteger jwksFetches;
    private volatile Supplier<Map<String, PublicKey>> jwks;
    private KillBillCognitoRealm realm;
    private SecurityConfig securityConfig;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
        otherKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
        jwksFetches = new AtomicInteger();
        jwks = () -> Map.of(KID, keyPair.getPublic());

        final Properties props = new Properties();
        props.setProperty("org.killbill.security.cognito.issuer", ISSUER);
        props.setProperty("org.killbill.security.cognito.audiences", AUD_PASSWORD + ", " + AUD_SSO);
        props.setProperty("org.killbill.security.cognito.jwksMinRefreshInterval", "1h");
        props.setProperty("org.killbill.security.cognito.jwksMaxAge", "6h");
        securityConfig = new AugmentedConfigurationObjectFactory(new SimplePropertyConfigSource(props)).build(SecurityConfig.class);

        realm = new KillBillCognitoRealm(securityConfig, clock, null) {
            @Override
            protected Map<String, PublicKey> fetchJwks() {
                jwksFetches.incrementAndGet();
                return jwks.get();
            }

            @Override
            protected Set<String> findPermissions(final String role) {
                if ("billing".equals(role)) {
                    return Set.of("account:create", "account:update");
                }
                return Set.of();
            }
        };
    }

    @Test(groups = "fast")
    public void testValidTokenAndPermissions() {
        final AuthenticationInfo info = realm.getAuthenticationInfo(bearer(validToken().compact()));
        final PrincipalCollection principals = info.getPrincipals();

        Assert.assertEquals(principals.getPrimaryPrincipal(), "cognito:member@example.com");
        Assert.assertTrue(principals.asList().contains(new KillBillCognitoRealm.CognitoRole("billing")));

        Assert.assertTrue(realm.isPermitted(principals, "account:create"));
        Assert.assertTrue(realm.isPermitted(principals, "account:update"));
        Assert.assertFalse(realm.isPermitted(principals, "payment:refund"));
    }

    @Test(groups = "fast")
    public void testSecondAudienceAccepted() {
        realm.getAuthenticationInfo(bearer(validToken().setAudience(AUD_SSO).compact()));
    }

    @Test(groups = "fast")
    public void testRoleWithoutPermissions() {
        final AuthenticationInfo info = realm.getAuthenticationInfo(bearer(validToken().claim("custom:role", "readonly").compact()));
        Assert.assertFalse(realm.isPermitted(info.getPrincipals(), "account:create"));
    }

    @Test(groups = "fast")
    public void testRejectedTokens() {
        final long now = clock.getUTCNow().getMillis();
        assertRejected(validToken().setAudience("other-client").compact());
        assertRejected(validToken().setIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_OTHER").compact());
        assertRejected(validToken().setExpiration(new Date(now - 2 * 3600 * 1000L)).compact());
        assertRejected(validToken().setExpiration(null).compact());
        assertRejected(validToken().claim("token_use", "access").compact());
        assertRejected(validToken().claim("custom:role", null).compact());
        assertRejected(validToken().claim("email", null).compact());
        assertRejected(validToken().setSubject(null).compact());
        // Signed with a key that is not in the JWKS
        assertRejected(validToken().signWith(otherKeyPair.getPrivate(), SignatureAlgorithm.RS256).compact());
        // Unknown kid
        assertRejected(validToken().setHeaderParam("kid", "kid-unknown").compact());
        // Payload tampered with after signing
        final String[] parts = validToken().compact().split("\\.");
        final String payload = new String(Base64.getUrlDecoder().decode(parts[1])).replace("billing", "admin");
        assertRejected(parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." + parts[2]);
        // Unsigned
        assertRejected(Jwts.builder().setHeaderParam("kid", KID).setIssuer(ISSUER).setAudience(AUD_PASSWORD).setSubject("s")
                           .claim("token_use", "id").claim("email", "x@example.com").claim("custom:role", "admin")
                           .setExpiration(new Date(now + 3600 * 1000L)).compact());
        assertRejected("not-a-jwt");
    }

    @Test(groups = "fast")
    public void testOnlyBearerTokensSupported() {
        Assert.assertFalse(realm.supports(new UsernamePasswordToken("admin", "password")));
        Assert.assertFalse(realm.supports(null));
        Assert.assertTrue(realm.supports(bearer("a.b.c")));
    }

    @Test(groups = "fast")
    public void testOtherRealmPrincipalsGetNothing() {
        final PrincipalCollection jdbcPrincipals = new SimplePrincipalCollection("cognito:member@example.com", "KillBillJdbcRealm");
        Assert.assertFalse(realm.isPermitted(jdbcPrincipals, "account:create"));
    }

    @Test(groups = "fast")
    public void testLocalUserWithTheSameNameGrantsNothing() throws Exception {
        // users / user_roles / roles_permissions as if a local user cognito:member@example.com existed with role root = *
        final ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.next()).thenReturn(true, false, true, false, true, false, true, false);
        Mockito.when(resultSet.getString(ArgumentMatchers.anyInt())).thenReturn("*");
        final PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        Mockito.when(statement.executeQuery()).thenReturn(resultSet);
        final Connection connection = Mockito.mock(Connection.class);
        Mockito.when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(statement);
        final DataSource dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);

        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(new SimplePropertyConfigSource(new Properties())).build(SecurityConfig.class);
        final KillBillJdbcRealm jdbcRealm = new KillBillJdbcRealm(dataSource, securityConfig);
        jdbcRealm.setAuthorizationCachingEnabled(false);

        // The local user gets its roles
        Assert.assertTrue(jdbcRealm.isPermitted(new SimplePrincipalCollection("cognito:member@example.com", jdbcRealm.getName()), "payment:refund"));

        // The Cognito user with the same name only gets its token role
        final PrincipalCollection principals = realm.getAuthenticationInfo(bearer(validToken().compact())).getPrincipals();
        Assert.assertEquals(principals.getPrimaryPrincipal(), "cognito:member@example.com");
        Assert.assertFalse(jdbcRealm.isPermitted(principals, "payment:refund"));
        Assert.assertFalse(realm.isPermitted(principals, "payment:refund"));
        Assert.assertTrue(realm.isPermitted(principals, "account:create"));
    }

    @Test(groups = "fast")
    public void testOtherRealmsGrantNothingToCognitoSubjects() {
        final Properties props = new Properties();
        props.setProperty("org.killbill.security.auth0.url", "http://127.0.0.1:1");
        props.setProperty("org.killbill.security.okta.url", "http://127.0.0.1:1");
        props.setProperty("org.killbill.security.ldap.url", "ldap://127.0.0.1:1");
        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(new SimplePropertyConfigSource(props)).build(SecurityConfig.class);
        // Any lookup by these realms would fail (nothing listens on port 1)
        final List<AuthorizingRealm> otherRealms = List.of(new KillBillAuth0Realm(securityConfig, clock),
                                                           new KillBillOktaRealm(securityConfig),
                                                           new KillBillJndiLdapRealm(securityConfig));

        final PrincipalCollection principals = realm.getAuthenticationInfo(bearer(validToken().compact())).getPrincipals();
        for (final AuthorizingRealm other : otherRealms) {
            other.setAuthorizationCachingEnabled(false);
            Assert.assertFalse(other.isPermitted(principals, "account:create"), other.getClass().getSimpleName());
        }

        // All realms together, as configured in Kill Bill: the token role is granted, nothing else
        final List<Realm> allRealms = new ArrayList<>(otherRealms);
        allRealms.add(realm);
        final ModularRealmAuthorizer authorizer = new ModularRealmAuthorizer(allRealms);
        Assert.assertTrue(authorizer.isPermitted(principals, "account:create"));
        Assert.assertFalse(authorizer.isPermitted(principals, "payment:refund"));
    }

    @Test(groups = "fast")
    public void testRoleChangesApplyDespiteTheAuthorizationCache() throws Exception {
        final Map<String, Set<String>> roles = new HashMap<>(Map.of("billing", Set.of("account:create", "account:update")));
        final KillBillCognitoRealm cachingRealm = new KillBillCognitoRealm(securityConfig, clock, null) {
            @Override
            protected Map<String, PublicKey> fetchJwks() {
                return Map.of(KID, keyPair.getPublic());
            }

            @Override
            protected Set<String> findPermissions(final String role) {
                return roles.getOrDefault(role, Set.of());
            }
        };
        cachingRealm.setCacheManager(new MemoryConstrainedCacheManager());
        Assert.assertTrue(cachingRealm.isAuthorizationCachingEnabled());
        final UserDao userDao = Mockito.mock(UserDao.class);
        final DefaultSecurityApi securityApi = new DefaultSecurityApi(userDao, Set.of(cachingRealm));

        final PrincipalCollection principals = cachingRealm.getAuthenticationInfo(bearer(validToken().compact())).getPrincipals();
        Assert.assertTrue(cachingRealm.isPermitted(principals, "account:update"));

        // Permission removed from the role: refused at once, although the same subject is cached
        roles.put("billing", Set.of("account:create"));
        securityApi.updateRoleDefinition("billing", List.of("account:create"), callContext);
        Assert.assertFalse(cachingRealm.isPermitted(principals, "account:update"));
        Assert.assertTrue(cachingRealm.isPermitted(principals, "account:create"));

        // Role defined after a token naming it was seen
        final PrincipalCollection newRolePrincipals = cachingRealm.getAuthenticationInfo(bearer(validToken().claim("custom:role", "late").compact())).getPrincipals();
        Assert.assertFalse(cachingRealm.isPermitted(newRolePrincipals, "invoice:credit"));
        roles.put("late", Set.of("invoice:credit"));
        securityApi.addRoleDefinition("late", List.of("invoice:credit"), callContext);
        Assert.assertTrue(cachingRealm.isPermitted(newRolePrincipals, "invoice:credit"));
    }

    @Test(groups = "fast")
    public void testRoleChangesKeepAuth0TokenPermissions() throws Exception {
        // Auth0 caches the permissions of a token when authenticating it, and cannot get them back later
        final KillBillAuth0Realm auth0Realm = new KillBillAuth0Realm(securityConfig, clock);
        auth0Realm.setCacheManager(new MemoryConstrainedCacheManager());
        final SimplePrincipalCollection auth0Principals = new SimplePrincipalCollection("auth0-user@example.com", auth0Realm.getName());
        final SimpleAuthorizationInfo tokenPermissions = new SimpleAuthorizationInfo();
        tokenPermissions.setStringPermissions(Set.of("invoice:credit"));
        auth0Realm.getAuthorizationCache().put(auth0Principals, tokenPermissions);

        final KillBillCognitoRealm cachingRealm = new KillBillCognitoRealm(securityConfig, clock, null) {
            @Override
            protected Map<String, PublicKey> fetchJwks() {
                return Map.of(KID, keyPair.getPublic());
            }

            @Override
            protected Set<String> findPermissions(final String role) {
                return Set.of("account:create");
            }
        };
        cachingRealm.setCacheManager(new MemoryConstrainedCacheManager());
        final PrincipalCollection cognitoPrincipals = cachingRealm.getAuthenticationInfo(bearer(validToken().compact())).getPrincipals();
        Assert.assertTrue(cachingRealm.isPermitted(cognitoPrincipals, "account:create"));
        Assert.assertEquals(cachingRealm.getAuthorizationCache().size(), 1);

        final DefaultSecurityApi securityApi = new DefaultSecurityApi(Mockito.mock(UserDao.class), Set.of(auth0Realm, cachingRealm));
        securityApi.updateRoleDefinition("billing", List.of("account:create"), callContext);

        // The Cognito cache is cleared, the Auth0 token permissions are kept
        Assert.assertEquals(cachingRealm.getAuthorizationCache().size(), 0);
        Assert.assertTrue(auth0Realm.isPermitted(auth0Principals, "invoice:credit"));
    }

    @Test(groups = "fast")
    public void testJwksRefreshIsRateLimited() {
        realm.getAuthenticationInfo(bearer(validToken().compact()));
        Assert.assertEquals(jwksFetches.get(), 1);
        // Known kid: no refetch
        realm.getAuthenticationInfo(bearer(validToken().compact()));
        Assert.assertEquals(jwksFetches.get(), 1);
        // Unknown kids within the refresh interval: no refetch
        assertRejected(validToken().setHeaderParam("kid", "kid-unknown-1").compact());
        assertRejected(validToken().setHeaderParam("kid", "kid-unknown-2").compact());
        Assert.assertEquals(jwksFetches.get(), 1);
    }

    @Test(groups = "fast")
    public void testCachedKeysExpire() {
        realm.getAuthenticationInfo(bearer(validToken().compact()));
        Assert.assertEquals(jwksFetches.get(), 1);

        // Within the max age, a known kid does not trigger a download
        clock.addDeltaFromReality(5 * 3600 * 1000L);
        realm.getAuthenticationInfo(bearer(validToken().compact()));
        Assert.assertEquals(jwksFetches.get(), 1);

        // Past the max age, the keys are downloaded again: a key removed from the pool is no longer trusted
        jwks = Map::of;
        clock.addDeltaFromReality(2 * 3600 * 1000L);
        assertRejected(validToken().compact());
        Assert.assertEquals(jwksFetches.get(), 2);
    }

    @Test(groups = "fast")
    public void testExpiredKeysKeptWhenTheDownloadFails() {
        realm.getAuthenticationInfo(bearer(validToken().compact()));
        Assert.assertEquals(jwksFetches.get(), 1);

        jwks = () -> {
            throw new SignatureException("JWKS unavailable");
        };
        clock.addDeltaFromReality(7 * 3600 * 1000L);
        // The download fails: the current keys are kept
        realm.getAuthenticationInfo(bearer(validToken().compact()));
        Assert.assertEquals(jwksFetches.get(), 2);
        // And the next attempt waits for the refresh interval
        realm.getAuthenticationInfo(bearer(validToken().compact()));
        Assert.assertEquals(jwksFetches.get(), 2);

        jwks = Map::of;
        clock.addDeltaFromReality(3600 * 1000L);
        assertRejected(validToken().compact());
        Assert.assertEquals(jwksFetches.get(), 3);
    }

    @Test(groups = "fast")
    public void testFailedFirstDownloadIsRateLimited() {
        jwks = () -> {
            throw new SignatureException("JWKS unavailable");
        };
        assertRejected(validToken().compact());
        assertRejected(validToken().compact());
        assertRejected(validToken().compact());
        Assert.assertEquals(jwksFetches.get(), 1);

        jwks = () -> Map.of(KID, keyPair.getPublic());
        clock.addDeltaFromReality(3600 * 1000L);
        realm.getAuthenticationInfo(bearer(validToken().compact()));
        Assert.assertEquals(jwksFetches.get(), 2);
    }

    @Test(groups = "fast")
    public void testJwksDownload() throws Exception {
        final RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        final String body = "{\"keys\":[{\"kid\":\"" + KID + "\",\"kty\":\"RSA\",\"n\":\"" + base64Url(publicKey.getModulus()) +
                            "\",\"e\":\"" + base64Url(publicKey.getPublicExponent()) + "\"}]}";
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok/.well-known/jwks.json", exchange -> respond(exchange, 200, body.getBytes(), body.length()));
        server.createContext("/error/.well-known/jwks.json", exchange -> respond(exchange, 500, "oops".getBytes(), 4));
        // Headers, then a body that never completes
        server.createContext("/stalled/.well-known/jwks.json", exchange -> {
            respond(exchange, 200, "{\"keys\":".getBytes(), body.length());
            try {
                Thread.sleep(10000);
            } catch (final InterruptedException ignored) {
            }
        });
        final ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        try {
            final String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Assert.assertEquals(((RSAPublicKey) downloadingRealm(base + "/ok").fetchJwks().get(KID)).getModulus(), publicKey.getModulus());

            try {
                downloadingRealm(base + "/error").fetchJwks();
                Assert.fail("HTTP 500 should fail");
            } catch (final SignatureException expected) {
                Assert.assertTrue(expected.getMessage().contains("HTTP 500"), expected.getMessage());
            }

            final long start = System.currentTimeMillis();
            try {
                downloadingRealm(base + "/stalled").fetchJwks();
                Assert.fail("A stalled body should time out");
            } catch (final SignatureException expected) {
            }
            Assert.assertTrue(System.currentTimeMillis() - start < 5000, "The read timeout must bound the body download");
        } finally {
            server.stop(0);
            // Also interrupts the stalled handler
            executor.shutdownNow();
        }
    }

    private KillBillCognitoRealm downloadingRealm(final String issuer) {
        final Properties props = new Properties();
        props.setProperty("org.killbill.security.cognito.issuer", issuer);
        props.setProperty("org.killbill.security.cognito.audiences", AUD_PASSWORD);
        props.setProperty("org.killbill.security.cognito.readTimeout", "500ms");
        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(new SimplePropertyConfigSource(props)).build(SecurityConfig.class);
        return new KillBillCognitoRealm(securityConfig, clock, null);
    }

    private static void respond(final com.sun.net.httpserver.HttpExchange exchange, final int status, final byte[] bytes, final long length) throws IOException {
        exchange.sendResponseHeaders(status, length);
        final OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.flush();
        if (bytes.length == length) {
            out.close();
        }
    }

    @Test(groups = "fast")
    public void testParseJwks() {
        final RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        final Map<String, Object> jwk = new HashMap<>();
        jwk.put("kid", KID);
        jwk.put("kty", "RSA");
        jwk.put("alg", "RS256");
        jwk.put("use", "sig");
        jwk.put("n", base64Url(publicKey.getModulus()));
        jwk.put("e", base64Url(publicKey.getPublicExponent()));
        final Map<String, Object> ecJwk = Map.of("kid", "ec", "kty", "EC");

        final Map<String, PublicKey> keys = KillBillCognitoRealm.parseJwks(Map.of("keys", List.of(jwk, ecJwk)));
        Assert.assertEquals(keys.size(), 1);
        Assert.assertEquals(((RSAPublicKey) keys.get(KID)).getModulus(), publicKey.getModulus());
        Assert.assertEquals(((RSAPublicKey) keys.get(KID)).getPublicExponent(), publicKey.getPublicExponent());
    }

    private JwtBuilder validToken() {
        final long now = clock.getUTCNow().getMillis();
        return Jwts.builder()
                   .setHeaderParam("kid", KID)
                   .setIssuer(ISSUER)
                   .setAudience(AUD_PASSWORD)
                   .setSubject("0b1c2d3e-sub")
                   .setIssuedAt(new Date(now))
                   .setExpiration(new Date(now + 3600 * 1000L))
                   .claim("token_use", "id")
                   .claim("email", "member@example.com")
                   .claim("custom:role", "billing")
                   .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256);
    }

    private void assertRejected(final String token) {
        try {
            realm.getAuthenticationInfo(bearer(token));
            Assert.fail("Token should have been rejected");
        } catch (final AuthenticationException expected) {
        }
    }

    private static HostAuthenticationToken bearer(final String token) {
        return new HostAuthenticationToken() {
            @Override
            public String getHost() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return token;
            }

            @Override
            public Object getCredentials() {
                return token;
            }
        };
    }

    private static String base64Url(final BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
