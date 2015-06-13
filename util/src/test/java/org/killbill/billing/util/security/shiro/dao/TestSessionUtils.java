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

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.shiro.session.mgt.SimpleSession;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class TestSessionUtils extends UtilTestSuiteNoDB {

    private static final long MINUTES_IN_MILLIS = 60 * 1000L;

    @Test(groups = "fast")
    public void testAccessedRecently() throws Exception {
        final Long t2 = System.currentTimeMillis();
        final Long t1 = t2 - (3 * MINUTES_IN_MILLIS);

        final SimpleSession session1 = new SimpleSession();
        final SimpleSession session2 = new SimpleSession();
        session1.setLastAccessTime(null);
        session2.setLastAccessTime(null);

        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2));

        session1.setLastAccessTime(new Date(t1));
        session2.setLastAccessTime(new Date(t2));

        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2));

        // For a timeout of 1 hour, 5% is 3 minutes
        session2.setTimeout(59 * MINUTES_IN_MILLIS);
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2));

        session2.setTimeout(60 * MINUTES_IN_MILLIS);
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2));

        session2.setTimeout(61 * MINUTES_IN_MILLIS);
        Assert.assertTrue(SessionUtils.accessedRecently(session1, session2));
    }

    @Test(groups = "fast")
    public void testAccessedRecentlyWithError() throws Exception {
        final Long t2 = System.currentTimeMillis();
        final Long t1 = t2 - (3 * MINUTES_IN_MILLIS);

        final SimpleSession session1 = new SimpleSession();
        final SimpleSession session2 = new SimpleSession();
        session1.setLastAccessTime(null);
        session2.setLastAccessTime(null);

        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 0L));
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 3 * MINUTES_IN_MILLIS - 1));
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 3 * MINUTES_IN_MILLIS));
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 3 * MINUTES_IN_MILLIS + 1));

        session1.setLastAccessTime(new Date(t1));

        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 0L));
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 3 * MINUTES_IN_MILLIS - 1));
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 3 * MINUTES_IN_MILLIS));
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 3 * MINUTES_IN_MILLIS + 1));

        session2.setLastAccessTime(new Date(t2));

        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 0L));
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 3 * MINUTES_IN_MILLIS - 1));
        Assert.assertFalse(SessionUtils.accessedRecently(session1, session2, 3 * MINUTES_IN_MILLIS));
        Assert.assertTrue(SessionUtils.accessedRecently(session1, session2, 3 * MINUTES_IN_MILLIS + 1));
    }

    @Test(groups = "fast")
    public void testSameSession() throws Exception {
        final SimpleSession session1 = new SimpleSession();
        final SimpleSession session2 = new SimpleSession();

        Assert.assertTrue(SessionUtils.sameSession(session1, session2));
        Assert.assertTrue(SessionUtils.sameSession(session2, session1));

        session1.setStartTimestamp(new Date(2 * System.currentTimeMillis()));
        Assert.assertFalse(SessionUtils.sameSession(session1, session2));
        Assert.assertFalse(SessionUtils.sameSession(session2, session1));

        session2.setStartTimestamp(session1.getStartTimestamp());
        Assert.assertTrue(SessionUtils.sameSession(session1, session2));
        Assert.assertTrue(SessionUtils.sameSession(session2, session1));

        session1.setTimeout(12345L);
        Assert.assertFalse(SessionUtils.sameSession(session1, session2));
        Assert.assertFalse(SessionUtils.sameSession(session2, session1));

        session2.setTimeout(session1.getTimeout());
        Assert.assertTrue(SessionUtils.sameSession(session1, session2));
        Assert.assertTrue(SessionUtils.sameSession(session2, session1));

        session1.setHost(UUID.randomUUID().toString());
        Assert.assertFalse(SessionUtils.sameSession(session1, session2));
        Assert.assertFalse(SessionUtils.sameSession(session2, session1));

        session2.setHost(session1.getHost());
        Assert.assertTrue(SessionUtils.sameSession(session1, session2));
        Assert.assertTrue(SessionUtils.sameSession(session2, session1));

        session1.setAttributes(buildAttributes(UUID.randomUUID()));
        Assert.assertFalse(SessionUtils.sameSession(session1, session2));
        Assert.assertFalse(SessionUtils.sameSession(session2, session1));

        session2.setAttributes(session1.getAttributes());
        Assert.assertTrue(SessionUtils.sameSession(session1, session2));
        Assert.assertTrue(SessionUtils.sameSession(session2, session1));
    }

    @Test(groups = "fast")
    public void testSameSessionAttributes() throws Exception {
        final UUID oneKey = UUID.randomUUID();
        final SimpleSession session1 = new SimpleSession();
        final SimpleSession session2 = new SimpleSession();
        final SimpleSession session3 = new SimpleSession();
        final Map<Object, Object> attributes = buildAttributes(oneKey);
        session1.setAttributes(attributes);
        session2.setAttributes(new LinkedHashMap<Object, Object>(attributes));

        Assert.assertFalse(SessionUtils.sameSessionAttributes(session1, null));
        Assert.assertFalse(SessionUtils.sameSessionAttributes(null, session1));
        Assert.assertFalse(SessionUtils.sameSessionAttributes(session1, session3));

        Assert.assertTrue(SessionUtils.sameSessionAttributes(null, null));
        Assert.assertTrue(SessionUtils.sameSessionAttributes(session1, session1));
        Assert.assertTrue(SessionUtils.sameSessionAttributes(session1, session2));
        Assert.assertTrue(SessionUtils.sameSessionAttributes(session2, session1));

        session2.removeAttribute(oneKey);

        Assert.assertFalse(SessionUtils.sameSessionAttributes(session1, session2));
        Assert.assertFalse(SessionUtils.sameSessionAttributes(session2, session1));
    }

    @Test(groups = "fast")
    public void testGetSessionAttributes() throws Exception {
        final SimpleSession session = new SimpleSession();
        final Map<Object, Object> attributes = buildAttributes(UUID.randomUUID());
        session.setAttributes(attributes);

        Assert.assertEquals(SessionUtils.getSessionAttributes(session), attributes);
    }

    private Map<Object, Object> buildAttributes(final UUID oneKey) {
        return ImmutableMap.<Object, Object>of(oneKey, 1L,
                                               UUID.randomUUID(), "2",
                                               UUID.randomUUID(), (short) 3,
                                               UUID.randomUUID(), 4,
                                               UUID.randomUUID(), UUID.randomUUID());
    }
}
