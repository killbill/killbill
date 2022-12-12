/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

import org.apache.shiro.session.Session;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestJDBCSessionDaoUnit extends UtilTestSuiteNoDB {

    private JDBCSessionDao createJdbcSessionDao() {
        final IDBI idbi = Mockito.mock(IDBI.class);
        final JDBCSessionDao toSpy = new JDBCSessionDao(idbi, idbi);
        return Mockito.spy(toSpy);
    }

    private Session createSession(final Serializable id) {
        final Session session = Mockito.mock(Session.class);
        Mockito.when(session.getId()).thenReturn(id);
        return session;
    }

    @Test(groups = "fast")
    public void testShouldUpdateSession() {
        final JDBCSessionDao jdbcSessionDao = createJdbcSessionDao();

        final Session a = createSession("A");
        final Session b = createSession("B");
        final Session c = createSession("C");

        jdbcSessionDao.noUpdateSessionsCache.put(a.getId(), true);
        jdbcSessionDao.noUpdateSessionsCache.put(b.getId(), false);
        jdbcSessionDao.noUpdateSessionsCache.put(c.getId(), true);

        Assert.assertFalse(jdbcSessionDao.shouldUpdateSession(a));
        Assert.assertTrue(jdbcSessionDao.shouldUpdateSession(b));
        Assert.assertFalse(jdbcSessionDao.shouldUpdateSession(c));
    }
}
