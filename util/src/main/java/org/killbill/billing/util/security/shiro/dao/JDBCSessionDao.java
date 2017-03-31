/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import java.io.IOException;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.eis.CachingSessionDAO;
import org.killbill.billing.util.UUIDs;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class JDBCSessionDao extends CachingSessionDAO {

    private static final Logger log = LoggerFactory.getLogger(JDBCSessionDao.class);

    private final JDBCSessionSqlDao jdbcSessionSqlDao;

    private final Cache<Serializable, Boolean> noUpdateSessionsCache = CacheBuilder.<Serializable, Boolean>newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();

    @Inject
    public JDBCSessionDao(final IDBI dbi) {
        this.jdbcSessionSqlDao = dbi.onDemand(JDBCSessionSqlDao.class);
    }

    @Override
    protected void doUpdate(final Session session) {
        if (shouldUpdateSession(session)) {
            jdbcSessionSqlDao.update(new SessionModelDao(session));
        }
    }

    @Override
    protected void doDelete(final Session session) {
        jdbcSessionSqlDao.delete(new SessionModelDao(session));
    }

    @Override
    protected Serializable doCreate(final Session session) {
        final UUID sessionId = UUIDs.randomUUID();
        // See SessionModelDao#toSimpleSession for why we use toString()
        final String sessionIdAsString = sessionId.toString();
        assignSessionId(session, sessionIdAsString);
        jdbcSessionSqlDao.create(new SessionModelDao(session));
        // Make sure to return a String here as well, or Shiro will cache the Session with a UUID key
        // while it is expecting String
        return sessionIdAsString;
    }

    @Override
    protected Session doReadSession(final Serializable sessionId) {
        // Shiro should not pass us a null sessionId, but be safe...
        if (sessionId == null) {
            return null;
        }

        final String sessionIdString = sessionId.toString();
        final SessionModelDao sessionModelDao = jdbcSessionSqlDao.read(sessionIdString);

        if (sessionModelDao == null) {
            return null;
        }

        try {
            return sessionModelDao.toSimpleSession();
        } catch (final IOException e) {
            log.warn("Corrupted cookie", e);
            return null;
        }
    }

    public void disableUpdatesForSession(final Session session) {
        noUpdateSessionsCache.put(session.getId(), Boolean.TRUE);
    }

    public void enableUpdatesForSession(final Session session) {
        noUpdateSessionsCache.invalidate(session.getId());
        doUpdate(session);
    }

    private boolean shouldUpdateSession(final Session session) {
        return noUpdateSessionsCache.getIfPresent(session.getId()) == Boolean.TRUE ? Boolean.FALSE : Boolean.TRUE;
    }
}
