/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.billing.util.security.shiro.dao;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.AbstractSessionManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

public abstract class SessionUtils {

    public SessionUtils() {}

    // Check if the session was recently accessed ("recently" means within 5% of the timeout length)
    public static boolean accessedRecently(final Session previousSession, final Session session) {
        final Long timeoutMillis = MoreObjects.firstNonNull(session.getTimeout(), AbstractSessionManager.DEFAULT_GLOBAL_SESSION_TIMEOUT);
        final Long errorMarginInMillis = 5L * timeoutMillis / 100;
        return accessedRecently(previousSession, session, errorMarginInMillis);
    }

    @VisibleForTesting
    static boolean accessedRecently(final Session previousSession, final Session session, final Long errorMarginInMillis) {
        return previousSession.getLastAccessTime() != null &&
               session.getLastAccessTime() != null &&
               session.getLastAccessTime().getTime() < previousSession.getLastAccessTime().getTime() + errorMarginInMillis;
    }

    public static boolean sameSession(final Session previousSession, final Session newSession) {
        return (previousSession.getStartTimestamp() != null ? previousSession.getStartTimestamp().compareTo(newSession.getStartTimestamp()) == 0 : newSession.getStartTimestamp() == null) &&
               (previousSession.getTimeout() == newSession.getTimeout()) &&
               (previousSession.getHost() != null ? previousSession.getHost().equals(newSession.getHost()) : newSession.getHost() == null) &&
               sameSessionAttributes(previousSession, newSession);
    }

    @VisibleForTesting
    static boolean sameSessionAttributes(@Nullable final Session previousSession, @Nullable final Session newSession) {
        final Map<Object, Object> previousSessionAttributes = getSessionAttributes(previousSession);
        final Map<Object, Object> newSessionAttributes = getSessionAttributes(newSession);
        return previousSessionAttributes != null ? previousSessionAttributes.equals(newSessionAttributes) : newSessionAttributes == null;
    }

    public static Map<Object, Object> getSessionAttributes(@Nullable final Session session) {
        if (session == null || session.getAttributeKeys() == null) {
            return null;
        }

        final Map<Object, Object> attributes = new LinkedHashMap<Object, Object>();
        for (final Object attributeKey : session.getAttributeKeys()) {
            attributes.put(attributeKey, session.getAttribute(attributeKey));
        }
        return attributes;
    }
}
