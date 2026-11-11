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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.HostAuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class TestKillBillCognitoRealm extends UtilTestSuiteNoDB {

    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST";
    private static final String AUD_PASSWORD = "password-client";
    private static final String AUD_SSO = "sso-client";
    private static final String KID = "kid-1";

    private KeyPair keyPair;
    private KeyPair otherKeyPair;
    private AtomicInteger jwksFetches;
    private KillBillCognitoRealm realm;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
        otherKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
        jwksFetches = new AtomicInteger();

        final Properties props = new Properties();
        props.setProperty("org.killbill.security.cognito.issuer", ISSUER);
        props.setProperty("org.killbill.security.cognito.audiences", AUD_PASSWORD + ", " + AUD_SSO);
        props.setProperty("org.killbill.security.cognito.jwksMinRefreshInterval", "1h");
        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(new SimplePropertyConfigSource(props)).build(SecurityConfig.class);

        realm = new KillBillCognitoRealm(securityConfig, clock, null) {
            @Override
            protected Map<String, PublicKey> fetchJwks() {
                jwksFetches.incrementAndGet();
                return Map.of(KID, keyPair.getPublic());
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
