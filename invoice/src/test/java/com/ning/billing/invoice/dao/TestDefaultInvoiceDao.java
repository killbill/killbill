/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.invoice.dao;

import java.util.Map;
import java.util.UUID;

import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.api.DefaultTagUserApi;
import com.ning.billing.util.tag.dao.MockTagDao;
import com.ning.billing.util.tag.dao.MockTagDefinitionDao;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

public class TestDefaultInvoiceDao {
    private TagUserApi tagUserApi;
    private DefaultInvoiceDao dao;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        final IDBI idbi = Mockito.mock(IDBI.class);
        final NextBillingDatePoster poster = Mockito.mock(NextBillingDatePoster.class);
        final TagDefinitionDao tagDefinitionDao = new MockTagDefinitionDao();
        final TagDao tagDao = new MockTagDao();
        tagUserApi = new DefaultTagUserApi(tagDefinitionDao, tagDao);
        dao = new DefaultInvoiceDao(idbi, poster, tagUserApi);
    }

    @Test(groups = "fast")
    public void testSetWrittenOff() throws Exception {
        final UUID invoiceId = UUID.randomUUID();

        final Map<String, Tag> beforeTags = tagUserApi.getTags(invoiceId, ObjectType.INVOICE);
        Assert.assertEquals(beforeTags.keySet().size(), 0);

        dao.setWrittenOff(invoiceId, Mockito.mock(CallContext.class));

        final Map<String, Tag> afterTags = tagUserApi.getTags(invoiceId, ObjectType.INVOICE);
        Assert.assertEquals(afterTags.keySet().size(), 1);
        final String name = ControlTagType.WRITTEN_OFF.toTagDefinition().getName();
        Assert.assertEquals(afterTags.get(name).getTagDefinitionName(), name);
    }

    @Test(groups = "fast")
    public void testRemoveWrittenOff() throws Exception {
        final UUID invoiceId = UUID.randomUUID();

        dao.setWrittenOff(invoiceId, Mockito.mock(CallContext.class));

        final Map<String, Tag> beforeTags = tagUserApi.getTags(invoiceId, ObjectType.INVOICE);
        Assert.assertEquals(beforeTags.keySet().size(), 1);
        final String name = ControlTagType.WRITTEN_OFF.toTagDefinition().getName();
        Assert.assertEquals(beforeTags.get(name).getTagDefinitionName(), name);

        dao.removeWrittenOff(invoiceId, Mockito.mock(CallContext.class));

        final Map<String, Tag> afterTags = tagUserApi.getTags(invoiceId, ObjectType.INVOICE);
        Assert.assertEquals(afterTags.keySet().size(), 0);
    }
}
