/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.Date;
import java.util.UUID;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.SimpleSession;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSessionModelDao extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testRoundTrip() throws Exception {
        final SimpleSession simpleSession = new SimpleSession();
        simpleSession.setStartTimestamp(new Date(System.currentTimeMillis() - 5000));
        simpleSession.setLastAccessTime(new Date(System.currentTimeMillis()));
        simpleSession.setTimeout(493934L);
        simpleSession.setHost(UUID.randomUUID().toString());
        simpleSession.setAttribute(UUID.randomUUID(), Short.MIN_VALUE);
        simpleSession.setAttribute(UUID.randomUUID(), Integer.MIN_VALUE);
        simpleSession.setAttribute(UUID.randomUUID(), Long.MIN_VALUE);
        simpleSession.setAttribute(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        // Test with Serializable objects
        simpleSession.setAttribute(UUID.randomUUID().toString(), UUID.randomUUID());
        simpleSession.setAttribute(UUID.randomUUID().toString(), new Date(1242));

        final SessionModelDao sessionModelDao = new SessionModelDao(simpleSession);
        Assert.assertEquals(sessionModelDao.getTimeout(), simpleSession.getTimeout());
        Assert.assertEquals(sessionModelDao.getHost(), simpleSession.getHost());
        Assert.assertTrue(sessionModelDao.getSessionData().length > 0);

        final Session retrievedSession = sessionModelDao.toSimpleSession();
        Assert.assertEquals(retrievedSession, simpleSession);
    }
}
