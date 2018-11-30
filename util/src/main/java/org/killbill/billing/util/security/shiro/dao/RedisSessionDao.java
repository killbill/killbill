/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.io.Serializable;
import java.util.UUID;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.eis.CachingSessionDAO;
import org.killbill.billing.util.UUIDs;

public class RedisSessionDao extends CachingSessionDAO {

    @Override
    protected Serializable doCreate(final Session session) {
        final UUID sessionId = UUIDs.randomUUID();
        // See SessionModelDao#toSimpleSession for why we use toString()
        final String sessionIdAsString = sessionId.toString();
        assignSessionId(session, sessionIdAsString);
        // Make sure to return a String here as well, or Shiro will cache the Session with a UUID key
        // while it is expecting String
        return sessionIdAsString;
    }

    protected Session doReadSession(final Serializable sessionId) {
        // Should never be executed
        throw new IllegalStateException("Session should be in Redis");
    }

    protected void doUpdate(final Session session) {
        // Does nothing - parent class persists to cache.
    }

    @Override
    protected void doDelete(final Session session) {
        // Does nothing - parent class removes from cache.
    }
}
