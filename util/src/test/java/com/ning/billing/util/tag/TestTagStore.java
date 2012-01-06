/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.util.tag;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.tag.dao.TagDescriptionDao;
import com.ning.billing.util.tag.dao.TagStoreDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test(groups={"util"})
public class TestTagStore {
    private final static String ACCOUNT_TYPE = "ACCOUNT";
    private final Clock clock = new DefaultClock();
    private final MysqlTestingHelper helper = new MysqlTestingHelper();
    private IDBI dbi;
    private TagDescription tag1;
    private TagDescription tag2;

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            final String utilDdl = IOUtils.toString(TestTagStore.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

            helper.startMysql();
            helper.initDb(utilDdl);

            dbi = helper.getDBI();

            tag1 = new DefaultTagDescription("tag1", "First tag", true, true, "test", clock.getUTCNow());
            tag2 = new DefaultTagDescription("tag2", "Second tag", false, false, "test", clock.getUTCNow());

            TagDescriptionDao dao = dbi.onDemand(TagDescriptionDao.class);
            dao.create(tag1);
            dao.create(tag2);
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql()
    {
        helper.stopMysql();
    }

    @Test
    public void testTagCreationAndRetrieval() {
        UUID accountId = UUID.randomUUID();

        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);
        Tag tag = new DefaultTag(tag2, "test", clock.getUTCNow());
        tagStore.add(tag);

        TagStoreDao dao = dbi.onDemand(TagStoreDao.class);
        dao.save(accountId.toString(), ACCOUNT_TYPE, tagStore.getEntityList());

        List<Tag> savedTags = dao.load(accountId.toString(), ACCOUNT_TYPE);
        assertEquals(savedTags.size(), 1);

        Tag savedTag = savedTags.get(0);
        assertEquals(savedTag.getTagDescriptionId(), tag.getTagDescriptionId());
        assertEquals(savedTag.getAddedBy(), tag.getAddedBy());
        assertEquals(savedTag.getDateAdded().compareTo(tag.getDateAdded()), 0);
        assertEquals(savedTag.getGenerateInvoice(), tag.getGenerateInvoice());
        assertEquals(savedTag.getName(), tag.getName());
        assertEquals(savedTag.getProcessPayment(), tag.getProcessPayment());
        assertEquals(savedTag.getId(), tag.getId());
    }
}
