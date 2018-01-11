/*
 * Copyright 2015-2018 Groupon, Inc
 * Copyright 2015-2018 The Billing Project, LLC
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

package org.apache.shiro.authc.pam;

import java.util.Collection;
import java.util.LinkedList;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.realm.Realm;
import org.killbill.billing.server.security.FirstSuccessfulStrategyWith540;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fix for https://issues.apache.org/jira/browse/SHIRO-540
 * Support for additional realms non injected
 */
public class ModularRealmAuthenticatorWith540 extends ModularRealmAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(ModularRealmAuthenticator.class);

    public ModularRealmAuthenticatorWith540(final Collection<Realm> realmsFromShiroIni, final ModularRealmAuthenticator delegate) {
        // Note: order matters (the first successful match will win)
        final Collection<Realm> realms = new LinkedList<Realm>(realmsFromShiroIni);
        realms.addAll(delegate.getRealms());
        setRealms(realms);
        setAuthenticationStrategy(delegate.getAuthenticationStrategy());
    }

    /**
     * Performs the multi-realm authentication attempt by calling back to a {@link AuthenticationStrategy} object
     * as each realm is consulted for {@code AuthenticationInfo} for the specified {@code token}.
     *
     * @param realms the multiple realms configured on this Authenticator instance.
     * @param token  the submitted AuthenticationToken representing the subject's (user's) log-in principals and credentials.
     * @return an aggregated AuthenticationInfo instance representing account data across all the successfully
     * consulted realms.
     */
    protected AuthenticationInfo doMultiRealmAuthentication(final Collection<Realm> realms, final AuthenticationToken token) {

        final AuthenticationStrategy strategy = getAuthenticationStrategy();

        AuthenticationInfo aggregate = strategy.beforeAllAttempts(realms, token);

        if (log.isTraceEnabled()) {
            log.trace("Iterating through {} realms for PAM authentication", realms.size());
        }

        for (final Realm realm : realms) {

            aggregate = strategy.beforeAttempt(realm, token, aggregate);

            if (realm.supports(token)) {

                log.trace("Attempting to authenticate token [{}] using realm [{}]", token, realm);

                AuthenticationInfo info = null;
                Throwable t = null;
                try {
                    info = realm.getAuthenticationInfo(token);
                } catch (final Throwable throwable) {
                    t = throwable;
                    if (log.isDebugEnabled()) {
                        final String msg = "Realm [" + realm + "] threw an exception during a multi-realm authentication attempt:";
                        log.debug(msg, t);
                    }
                }

                aggregate = strategy.afterAttempt(realm, token, info, aggregate, t);

                if (strategy instanceof FirstSuccessfulStrategyWith540) {
                    // check if we should check the next realm, or just stop here.
                    if (!((FirstSuccessfulStrategyWith540) strategy).continueAfterAttempt(info, aggregate, t)) {
                        log.trace("Will not consult any other realms for authentication, last realm [{}].", realm);
                        break;
                    }
                }

            } else {
                log.debug("Realm [{}] does not support token {}.  Skipping realm.", realm, token);
            }
        }

        aggregate = strategy.afterAllAttempts(token, aggregate);

        return aggregate;
    }
}
