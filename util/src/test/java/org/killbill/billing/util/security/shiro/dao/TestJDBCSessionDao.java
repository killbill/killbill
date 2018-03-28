/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
import java.util.Date;
import java.util.UUID;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.SimpleSession;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestJDBCSessionDao extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testH2AndInvalidSessionId() {
        final JDBCSessionDao jdbcSessionDao = new JDBCSessionDao(dbi, roDbi);

        // We need to create some data to force H2 to build the query
        // (otherwise, the read path is optimized and the bug is not triggered)
        final SimpleSession session = createSession();
        jdbcSessionDao.doCreate(session);

        // Make sure this doesn't throw any exception on H2
        Assert.assertNull(jdbcSessionDao.doReadSession(UUID.randomUUID()));
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        // Note! We are testing the do* methods here to bypass the caching layer
        final JDBCSessionDao jdbcSessionDao = new JDBCSessionDao(dbi, roDbi);

        // Retrieve
        final SimpleSession session = createSession();
        Assert.assertNull(jdbcSessionDao.doReadSession(session.getId()));

        // Create
        final Serializable sessionId = jdbcSessionDao.doCreate(session);
        final Session retrievedSession = jdbcSessionDao.doReadSession(sessionId);
        Assert.assertEquals(retrievedSession, session);

        // Update
        final String newHost = UUID.randomUUID().toString();
        Assert.assertNotEquals(retrievedSession.getHost(), newHost);
        session.setHost(newHost);
        jdbcSessionDao.doUpdate(session);
        Assert.assertEquals(jdbcSessionDao.doReadSession(sessionId).getHost(), newHost);

        // Delete
        jdbcSessionDao.doDelete(session);
        Assert.assertNull(jdbcSessionDao.doReadSession(session.getId()));
    }

    private SimpleSession createSession() {
        final SimpleSession simpleSession = new SimpleSession();
        simpleSession.setStartTimestamp(new Date(System.currentTimeMillis() - 5000));
        simpleSession.setLastAccessTime(new Date(System.currentTimeMillis()));
        simpleSession.setTimeout(493934L);
        simpleSession.setHost(UUID.randomUUID().toString());
        simpleSession.setAttribute(UUID.randomUUID().toString(), Short.MIN_VALUE);
        simpleSession.setAttribute(UUID.randomUUID().toString(), Integer.MIN_VALUE);
        simpleSession.setAttribute(UUID.randomUUID().toString(), Long.MIN_VALUE);
        simpleSession.setAttribute(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        // Test with Serializable objects
        simpleSession.setAttribute(UUID.randomUUID().toString(), UUID.randomUUID());
        simpleSession.setAttribute(UUID.randomUUID().toString(), new Date(1242));
        return simpleSession;
    }
}
