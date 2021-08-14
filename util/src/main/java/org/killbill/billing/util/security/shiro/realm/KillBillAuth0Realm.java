/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.Key;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.apache.shiro.subject.MutablePrincipalCollection;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig.Builder;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Clock;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolver;
import io.jsonwebtoken.security.SignatureException;

public class KillBillAuth0Realm extends AuthorizingRealm {

    private static final Logger log = LoggerFactory.getLogger(KillBillAuth0Realm.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String USER_AGENT = "KillBill/1.0";
    private static final int DEFAULT_TIMEOUT_SECS = 70;

    private static final int CACHE_MAXIMUM_SIZE = 15;
    private static final int CACHE_TIMEOUT_MINUTES = 15;

    private final Cache<String, PublicKey> keys;

    private final SecurityConfig securityConfig;
    private final AsyncHttpClient httpClient;
    private final JwtParser jwtParser;

    @Inject
    public KillBillAuth0Realm(final SecurityConfig securityConfig, final org.killbill.clock.Clock clock) {
        this.securityConfig = securityConfig;
        final Builder cfg = new Builder().setUserAgent(USER_AGENT)
                                         .setConnectTimeout(Math.toIntExact(securityConfig.getShiroAuth0ConnectTimeout().getMillis()))
                                         .setReadTimeout((int) securityConfig.getShiroAuth0ReadTimeout().getMillis())
                                         .setRequestTimeout((int) securityConfig.getShiroAuth0RequestTimeout().getMillis());
        this.httpClient = new DefaultAsyncHttpClient(cfg.build());
        final JwtParserBuilder jwtParserBuilder = Jwts.parserBuilder();
        if (securityConfig.getShiroAuth0Audience() != null) {
            jwtParserBuilder.requireAudience(securityConfig.getShiroAuth0Audience());
        }
        if (securityConfig.getShiroAuth0Issuer() != null) {
            jwtParserBuilder.requireIssuer(securityConfig.getShiroAuth0Issuer());
        }
        this.jwtParser = jwtParserBuilder
                             .setClock(new Clock() {
                                 @Override
                                 public Date now() {
                                     return clock.getUTCNow().toDate();
                                 }
                             })
                             .setAllowedClockSkewSeconds(securityConfig.getShiroAuth0AllowedClockSkew().getMillis() / 1000)
                             .setSigningKeyResolver(new SigningKeyResolver() {
                                 @Override
                                 public Key resolveSigningKey(final JwsHeader header, final Claims claims) {
                                     return getKey(header);
                                 }

                                 @Override
                                 public Key resolveSigningKey(final JwsHeader header, final String plaintext) {
                                     return getKey(header);
                                 }

                                 private Key getKey(final JwsHeader<?> header) {
                                     final String keyId = header.getKeyId();
                                     if (keyId == null) {
                                         throw new SignatureException("Key ID is required");
                                     }

                                     final PublicKey publicKey;
                                     try {
                                         publicKey = keys.get(keyId, new Callable<PublicKey>() {
                                             @Override
                                             public PublicKey call() throws Exception {
                                                 return loadPublicKey(keyId);
                                             }
                                         });
                                     } catch (final ExecutionException e) {
                                         throw new SignatureException("Unknown signing key ID");
                                     }

                                     if (publicKey == null) {
                                         throw new SignatureException("Unknown signing key ID");
                                     }

                                     return publicKey;
                                 }
                             })
                             .build();
        this.keys = CacheBuilder.newBuilder()
                                .maximumSize(CACHE_MAXIMUM_SIZE)
                                .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                                .build();
    }

    @Override
    public boolean supports(final AuthenticationToken token) {
        // Cannot check specifically for BearerToken as it's not visible (fork is in killbill-server)
        return token != null && HostAuthenticationToken.class.isAssignableFrom(token.getClass());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(final PrincipalCollection principals) {
        final String username = (String) getAvailablePrincipal(principals);

        final SimpleAuthorizationInfo simpleAuthorizationInfo = new SimpleAuthorizationInfo(null);

        final String token = getBearerToken();

        final String auth0UserId = findAuth0UserId(username, token);
        if (auth0UserId == null) {
            return simpleAuthorizationInfo;
        }

        final Set<String> stringPermissions = findAuth0UserPermissions(auth0UserId, token);
        simpleAuthorizationInfo.setStringPermissions(stringPermissions);

        return simpleAuthorizationInfo;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            final UsernamePasswordToken upToken = (UsernamePasswordToken) token;
            if (doAuthenticate(upToken)) {
                // Credentials are valid
                return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
            }
        } else {
            final String bearerToken = (String) token.getPrincipal();
            final Claims claims = verifyJWT(bearerToken);
            // Credentials are valid

            // This config must match the one in Kaui
            final Object principal = claims.get(securityConfig.getShiroAuth0UsernameClaim());

            // For the JWT to contains the permissions, the `Add Permissions in the Access Token` setting must be turned on in Auth0
            if (claims.containsKey("permissions") && claims.get("permissions") instanceof Iterable) {
                // In order to use the permissions from the JWT (and avoid calling Auth0 later on), we need to eagerly cache them,
                // as doGetAuthorizationInfo won't have access to the token
                final org.apache.shiro.cache.Cache<Object, AuthorizationInfo> authorizationCache = getAuthorizationCache();
                // Should never be null (initialized via init())
                if (authorizationCache != null) {
                    final SimpleAuthorizationInfo simpleAuthorizationInfo = new SimpleAuthorizationInfo(null);
                    final Set<String> permissions = new HashSet<String>();
                    for (final Object permission : (Iterable) claims.get("permissions")) {
                        permissions.add(permission.toString());
                    }
                    simpleAuthorizationInfo.setStringPermissions(permissions);

                    final MutablePrincipalCollection principals = new SimplePrincipalCollection();
                    principals.add(principal, getName());
                    final Object authorizationCacheKey = getAuthorizationCacheKey(principals);

                    authorizationCache.put(authorizationCacheKey, simpleAuthorizationInfo);
                }
            }

            return new SimpleAuthenticationInfo(principal, token.getCredentials(), getName());
        }

        throw new AuthenticationException("Auth0 authentication failed");
    }

    private boolean doAuthenticate(final UsernamePasswordToken upToken) {
        final BoundRequestBuilder builder = httpClient.preparePost(securityConfig.getShiroAuth0Url() + "/oauth/token");
        builder.addFormParam("client_id", securityConfig.getShiroAuth0ClientId());
        builder.addFormParam("client_secret", securityConfig.getShiroAuth0ClientSecret());
        builder.addFormParam("audience", securityConfig.getShiroAuth0APIIdentifier());
        builder.addFormParam("grant_type", "http://auth0.com/oauth/grant-type/password-realm");
        builder.addFormParam("realm", securityConfig.getShiroAuth0DatabaseConnectionName());
        builder.addFormParam("username", upToken.getUsername());
        builder.addFormParam("password", String.valueOf(upToken.getPassword()));

        builder.addHeader("Content-Type", "application/x-www-form-urlencoded");

        final Response response;
        try {
            final ListenableFuture<Response> futureStatus =
                    builder.execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(final Response response) throws Exception {
                            return response;
                        }
                    });
            response = futureStatus.get(DEFAULT_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (final TimeoutException toe) {
            log.warn("Timeout while connecting to Auth0", toe);
            throw new AuthenticationException(toe);
        } catch (final Exception e) {
            log.warn("Error while connecting to Auth0", e);
            throw new AuthenticationException(e);
        }

        return isAuthenticated(response);
    }

    private boolean isAuthenticated(final Response auth0RawResponse) {
        try {
            final Map<String, Object> auth0Response = mapper.readValue(auth0RawResponse.getResponseBodyAsStream(), new TypeReference<Map<String, Object>>() {});
            if (auth0Response.containsKey("access_token")) {
                return true;
            } else {
                log.warn("Auth0 authentication failed: {}", auth0Response);
                return false;
            }
        } catch (final IOException e) {
            log.warn("Unable to read response from Auth0", e);
            throw new AuthenticationException(e);
        }
    }

    private String getBearerToken() {
        final BoundRequestBuilder builder = httpClient.preparePost(securityConfig.getShiroAuth0Url() + "/oauth/token");
        builder.addFormParam("client_id", securityConfig.getShiroAuth0ClientId());
        builder.addFormParam("client_secret", securityConfig.getShiroAuth0ClientSecret());
        builder.addFormParam("audience", securityConfig.getShiroAuth0Url() + "/api/v2/");
        builder.addFormParam("grant_type", "client_credentials");

        builder.addHeader("Content-Type", "application/x-www-form-urlencoded");

        final Response response;
        try {
            final ListenableFuture<Response> futureStatus =
                    builder.execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(final Response response) throws Exception {
                            return response;
                        }
                    });
            response = futureStatus.get(DEFAULT_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (final TimeoutException toe) {
            log.warn("Timeout while connecting to Auth0", toe);
            throw new AuthenticationException(toe);
        } catch (final Exception e) {
            log.warn("Error while connecting to Auth0", e);
            throw new AuthenticationException(e);
        }

        final Map<String, Object> auth0Response;
        try {
            auth0Response = mapper.readValue(response.getResponseBodyAsStream(), new TypeReference<Map<String, Object>>() {});
        } catch (final Exception e) {
            log.warn("Unable to read response from Auth0", e);
            throw new AuthorizationException(e);
        }

        final Object accessToken = auth0Response.get("access_token");
        if (accessToken == null) {
            throw new AuthorizationException("Unable to generate Bearer token");
        }
        return (String) accessToken;
    }

    private String findAuth0UserId(final String username, final String token) {
        final String path;
        try {
            path = "/api/v2/users-by-email?email=" + URLEncoder.encode(username, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            // Should never happen
            throw new IllegalStateException(e);
        }

        final Response auth0RawResponse = doGetRequest(path, token);
        try {
            final List<Map<String, Object>> auth0Response = mapper.readValue(auth0RawResponse.getResponseBodyAsStream(), new TypeReference<List<Map<String, Object>>>() {});
            if (auth0Response == null) {
                log.warn("Unable to find user {} in Auth0", username);
                return null;
            } else if (auth0Response.size() > 1) {
                log.warn("Too many users for {} in Auth0", username);
                return null;
            }
            return (String) auth0Response.get(0).get("user_id");
        } catch (final IOException e) {
            log.warn("Unable to read response from Auth0", e);
            throw new AuthorizationException(e);
        }
    }

    private Set<String> findAuth0UserPermissions(final String userId, final String token) {
        final String path;
        try {
            path = "/api/v2/users/" + URLEncoder.encode(userId, "UTF-8") + "/permissions";
        } catch (final UnsupportedEncodingException e) {
            // Should never happen
            throw new IllegalStateException(e);
        }

        final Response auth0RawResponse = doGetRequest(path, token);
        try {
            final List<Map<String, Object>> auth0Response = mapper.readValue(auth0RawResponse.getResponseBodyAsStream(), new TypeReference<List<Map<String, Object>>>() {});
            final Set<String> permissions = new HashSet<String>();
            for (final Map<String, Object> group : auth0Response) {
                final Object permission = group.get("permission_name");
                if (permission != null) {
                    permissions.add((String) permission);
                }
            }
            return permissions;
        } catch (final IOException e) {
            log.warn("Unable to read response from Auth0", e);
            throw new AuthorizationException(e);
        }
    }

    private Response doGetRequest(final String path, final String token) {
        final BoundRequestBuilder builder = httpClient.prepareGet(securityConfig.getShiroAuth0Url() + path);
        builder.addHeader("Authorization", "Bearer " + token);
        final Response response;
        try {
            final ListenableFuture<Response> futureStatus =
                    builder.execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(final Response response) throws Exception {
                            return response;
                        }
                    });
            response = futureStatus.get(DEFAULT_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (final TimeoutException toe) {
            log.warn("Timeout while connecting to Auth0", toe);
            throw new AuthorizationException(toe);
        } catch (final Exception e) {
            log.warn("Error while connecting to Auth0", e);
            throw new AuthorizationException(e);
        }
        return response;
    }

    @VisibleForTesting
    Claims verifyJWT(final String token) throws AuthenticationException {
        if (Strings.isNullOrEmpty(token)) {
            throw new AuthenticationException("ID token is required but missing");
        }

        final Jws<Claims> decoded;
        try {
            decoded = jwtParser.parseClaimsJws(token);
        } catch (final JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            throw new AuthenticationException("ID token could not be decoded", e);
        }

        if (Strings.isNullOrEmpty(decoded.getBody().getSubject())) {
            throw new AuthenticationException("Subject (sub) claim must be a string present in the ID token");
        }

        return decoded.getBody();
    }

    private PublicKey loadPublicKey(final String keyId) {
        final BoundRequestBuilder builder = httpClient.prepareGet(securityConfig.getShiroAuth0Url() + "/.well-known/jwks.json");
        final Response response;
        try {
            final ListenableFuture<Response> futureStatus =
                    builder.execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(final Response response) throws Exception {
                            return response;
                        }
                    });
            response = futureStatus.get(DEFAULT_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (final TimeoutException toe) {
            throw new SignatureException("Timeout while connecting to Auth0 to fetch public keys", toe);
        } catch (final Exception e) {
            throw new SignatureException("Error while connecting to Auth0 to fetch public keys", e);
        }

        final Map<String, List<Map<String, Object>>> keysResponse;
        try {
            keysResponse = mapper.readValue(response.getResponseBodyAsStream(), new TypeReference<Map<String, List<Map<String, Object>>>>() {});
        } catch (final IOException e) {
            throw new SignatureException("Unable to read public keys from Auth0", e);
        }

        if (keysResponse.get("keys") == null || keysResponse.get("keys").isEmpty()) {
            throw new SignatureException("Auth0 returned no key");
        }

        final List<Map<String, Object>> newKeys = keysResponse.get("keys");
        for (final Map<String, Object> newKey : newKeys) {
            if (newKey.get("kid") == null || !newKey.get("kid").equals(keyId) || newKey.get("kty") == null) {
                continue;
            }

            final String kty = (String) newKey.get("kty");
            switch (kty) {
                case "RSA":
                    final BigInteger modulus = getBigInteger(newKey.get("n"));
                    final BigInteger exponent = getBigInteger(newKey.get("e"));
                    if (modulus == null || exponent == null) {
                        continue;
                    }
                    return new RSAPublicKey() {
                        @Override
                        public BigInteger getPublicExponent() {
                            return exponent;
                        }

                        @Override
                        public String getAlgorithm() {
                            return "RSA";
                        }

                        @Override
                        public String getFormat() {
                            return "JWK";
                        }

                        @Override
                        public byte[] getEncoded() {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public BigInteger getModulus() {
                            return modulus;
                        }
                    };
                case "EC":
                    final String curveName = (String) newKey.get("crv");
                    final BigInteger x = getBigInteger(newKey.get("x"));
                    final BigInteger y = getBigInteger(newKey.get("y"));
                    if (curveName == null || x == null || y == null) {
                        continue;
                    }
                    final ECParameterSpec curve = EcCurve.tryGet(curveName);
                    if (curve == null) {
                        continue;
                    }
                    final ECPoint w = new ECPoint(x, y);
                    return new ECPublicKey() {
                        @Override
                        public ECPoint getW() {
                            return w;
                        }

                        @Override
                        public String getAlgorithm() {
                            return "EC";
                        }

                        @Override
                        public String getFormat() {
                            return "JWK";
                        }

                        @Override
                        public byte[] getEncoded() {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public ECParameterSpec getParams() {
                            return curve;
                        }
                    };
                default:
            }
        }

        throw new SignatureException("Could not find Auth0 public key " + keyId);
    }

    private BigInteger getBigInteger(final Object value) {
        if (value == null) {
            return null;
        }
        return new BigInteger(1, Base64.getUrlDecoder().decode((String) value));
    }

    private static final class EcCurve {

        public static final ECParameterSpec P_256;
        public static final ECParameterSpec SECP256K1;
        public static final ECParameterSpec P_384;
        public static final ECParameterSpec P_521;

        private EcCurve() {}

        static {
            // Values obtained from org.bouncycastle.jce.ECNamedCurveTable

            P_256 = new ECParameterSpec(
                    new EllipticCurve(
                            new ECFieldFp(new BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853951")),
                            new BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853948"),
                            new BigInteger("41058363725152142129326129780047268409114441015993725554835256314039467401291")),
                    new ECPoint(
                            new BigInteger("48439561293906451759052585252797914202762949526041747995844080717082404635286"),
                            new BigInteger("36134250956749795798585127919587881956611106672985015071877198253568414405109")),
                    new BigInteger("115792089210356248762697446949407573529996955224135760342422259061068512044369"),
                    1);

            SECP256K1 = new ECParameterSpec(
                    new EllipticCurve(
                            new ECFieldFp(new BigInteger("115792089237316195423570985008687907853269984665640564039457584007908834671663")),
                            new BigInteger("0"),
                            new BigInteger("7")),
                    new ECPoint(
                            new BigInteger("55066263022277343669578718895168534326250603453777594175500187360389116729240"),
                            new BigInteger("32670510020758816978083085130507043184471273380659243275938904335757337482424")),
                    new BigInteger("115792089237316195423570985008687907852837564279074904382605163141518161494337"),
                    1);

            P_384 = new ECParameterSpec(
                    new EllipticCurve(
                            new ECFieldFp(new BigInteger("39402006196394479212279040100143613805079739270465446667948293404245721771496870329047266088258938001861606973112319")),
                            new BigInteger("39402006196394479212279040100143613805079739270465446667948293404245721771496870329047266088258938001861606973112316"),
                            new BigInteger("27580193559959705877849011840389048093056905856361568521428707301988689241309860865136260764883745107765439761230575")),
                    new ECPoint(
                            new BigInteger("26247035095799689268623156744566981891852923491109213387815615900925518854738050089022388053975719786650872476732087"),
                            new BigInteger("8325710961489029985546751289520108179287853048861315594709205902480503199884419224438643760392947333078086511627871")),
                    new BigInteger("39402006196394479212279040100143613805079739270465446667946905279627659399113263569398956308152294913554433653942643"),
                    1);

            P_521 = new ECParameterSpec(
                    new EllipticCurve(
                            new ECFieldFp(new BigInteger(
                                    "6864797660130609714981900799081393217269435300143305409394463459185543183397656052122559640661454554977296311391480858037121987999716643812574028291115057151")),
                            new BigInteger(
                                    "6864797660130609714981900799081393217269435300143305409394463459185543183397656052122559640661454554977296311391480858037121987999716643812574028291115057148"),
                            new BigInteger(
                                    "1093849038073734274511112390766805569936207598951683748994586394495953116150735016013708737573759623248592132296706313309438452531591012912142327488478985984")),
                    new ECPoint(
                            new BigInteger(
                                    "2661740802050217063228768716723360960729859168756973147706671368418802944996427808491545080627771902352094241225065558662157113545570916814161637315895999846"),
                            new BigInteger(
                                    "3757180025770020463545507224491183603594455134769762486694567779615544477440556316691234405012945539562144444537289428522585666729196580810124344277578376784")),
                    new BigInteger(
                            "6864797660130609714981900799081393217269435300143305409394463459185543183397655394245057746333217197532963996371363321113864768612440380340372808892707005449"),
                    1);
        }

        public static ECParameterSpec tryGet(final String name) {
            if ("P-256".equals(name)) {
                return P_256;
            }
            if ("secp256k1".equals(name)) {
                return SECP256K1;
            }
            if ("P-384".equals(name)) {
                return P_384;
            }
            if ("P-521".equals(name)) {
                return P_521;
            }
            return null;
        }
    }
}

