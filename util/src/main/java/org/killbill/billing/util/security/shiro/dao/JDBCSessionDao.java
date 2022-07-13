/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.eis.CachingSessionDAO;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.entity.dao.DBRouter;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.commons.utils.cache.Cache;
import org.killbill.commons.utils.cache.DefaultCache;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class JDBCSessionDao extends CachingSessionDAO {

    private static final int CACHE_MAX_SIZE = 20;
    private static final int CACHE_TIMEOUT_IN_SECONDS = 5;

    private static final Logger log = LoggerFactory.getLogger(JDBCSessionDao.class);

    private final DBRouter<JDBCSessionSqlDao> dbRouter;

    @VisibleForTesting
    final Cache<Serializable, Boolean> noUpdateSessionsCache = new DefaultCache<>(CACHE_MAX_SIZE, CACHE_TIMEOUT_IN_SECONDS, DefaultCache.noCacheLoader());

    @Inject
    public JDBCSessionDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi) {
        this.dbRouter = new DBRouter<JDBCSessionSqlDao>(dbi, roDbi, JDBCSessionSqlDao.class);
    }

    @Override
    protected void doUpdate(final Session session) {
        if (shouldUpdateSession(session)) {
            dbRouter.onDemand(false).update(new SessionModelDao(session));
        }
    }

    @Override
    protected void doDelete(final Session session) {
        dbRouter.onDemand(false).delete(new SessionModelDao(session));
    }

    @Override
    protected Serializable doCreate(final Session session) {
        final UUID sessionId = UUIDs.randomUUID();
        // See SessionModelDao#toSimpleSession for why we use toString()
        final String sessionIdAsString = sessionId.toString();
        assignSessionId(session, sessionIdAsString);
        dbRouter.onDemand(false).create(new SessionModelDao(session));
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
        final SessionModelDao sessionModelDao = dbRouter.onDemand(true).read(sessionIdString);

        if (sessionModelDao == null) {
            return null;
        }

        return toSession(sessionModelDao);
    }

    @Override
    public Collection<Session> getActiveSessions() {
        final Collection<Session> cachedActiveSessions = super.getActiveSessions();
        // To make sure the ValidatingSessionManager purges old sessions on disk
        final List<SessionModelDao> oldActiveSessionsOnDisk = dbRouter.onDemand(true).findOldActiveSessions();

        final Collection<Session> activeSessions = new LinkedList<Session>(cachedActiveSessions);
        for (final SessionModelDao sessionModelDao : oldActiveSessionsOnDisk) {
            activeSessions.add(toSession(sessionModelDao));
        }
        return activeSessions;
    }

    public void disableUpdatesForSession(final Session session) {
        noUpdateSessionsCache.put(session.getId(), Boolean.TRUE);
    }

    public void enableUpdatesForSession(final Session session) {
        noUpdateSessionsCache.invalidate(session.getId());
        doUpdate(session);
    }

    @VisibleForTesting
    boolean shouldUpdateSession(final Session session) {
        return noUpdateSessionsCache.get(session.getId()) == Boolean.TRUE ? Boolean.FALSE : Boolean.TRUE;
    }

    private Session toSession(final SessionModelDao sessionModelDao) {
        try {
            return sessionModelDao.toSimpleSession();
        } catch (final IOException e) {
            log.warn("Corrupted cookie", e);
            return null;
        }
    }
}
