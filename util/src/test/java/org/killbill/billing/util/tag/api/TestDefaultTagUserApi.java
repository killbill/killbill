/*
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

package org.killbill.billing.util.tag.api;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.mockito.Mockito;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDefaultTagUserApi extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testSaveTagWithAccountRecordId() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final Long accountRecordId = 19384012L;

        final ImmutableAccountData immutableAccountData = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(immutableAccountInternalApi.getImmutableAccountDataByRecordId(Mockito.<Long>eq(accountRecordId), Mockito.<InternalTenantContext>any())).thenReturn(immutableAccountData);

        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into accounts (record_id, id, external_key, email, name, first_name_length, reference_time, time_zone, created_date, created_by, updated_date, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               accountRecordId, accountId.toString(), accountId.toString(), "yo@t.com", "toto", 4, new Date(), "UTC", new Date(), "i", new Date(), "j");

                return null;
            }
        });

        checkPagination(0);

        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagUserApi.addTags(accountId, ObjectType.ACCOUNT, ImmutableList.<UUID>of(ControlTagType.WRITTEN_OFF.getId()), callContext);
        assertListenerStatus();

        checkPagination(1);

        // Verify the tag was saved
        final List<Tag> tags = tagUserApi.getTagsForObject(accountId, ObjectType.ACCOUNT, true, callContext);
        Assert.assertEquals(tags.size(), 1);
        Assert.assertEquals(tags.get(0).getTagDefinitionId(), ControlTagType.WRITTEN_OFF.getId());
        Assert.assertEquals(tags.get(0).getObjectId(), accountId);
        Assert.assertEquals(tags.get(0).getObjectType(), ObjectType.ACCOUNT);
        // Verify the account_record_id was populated
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                final List<Map<String, Object>> values = handle.select("select account_record_id from tags where object_id = ?", accountId.toString());
                Assert.assertEquals(values.size(), 1);
                Assert.assertEquals(values.get(0).keySet().size(), 1);
                Assert.assertEquals(Long.valueOf(values.get(0).get("account_record_id").toString()), accountRecordId);
                return null;
            }
        });

        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagUserApi.removeTags(accountId, ObjectType.ACCOUNT, ImmutableList.<UUID>of(ControlTagType.WRITTEN_OFF.getId()), callContext);
        assertListenerStatus();

        List<Tag> remainingTags = tagUserApi.getTagsForObject(accountId, ObjectType.ACCOUNT, false, callContext);
        Assert.assertEquals(remainingTags.size(), 0);

        checkPagination(0);

        // Add again the tag
        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagUserApi.addTags(accountId, ObjectType.ACCOUNT, ImmutableList.<UUID>of(ControlTagType.WRITTEN_OFF.getId()), callContext);
        assertListenerStatus();

        remainingTags = tagUserApi.getTagsForObject(accountId, ObjectType.ACCOUNT, false, callContext);
        Assert.assertEquals(remainingTags.size(), 1);

        checkPagination(1);

        // Delete again
        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagUserApi.removeTags(accountId, ObjectType.ACCOUNT, ImmutableList.<UUID>of(ControlTagType.WRITTEN_OFF.getId()), callContext);
        assertListenerStatus();

        remainingTags = tagUserApi.getTagsForObject(accountId, ObjectType.ACCOUNT, false, callContext);
        Assert.assertEquals(remainingTags.size(), 0);

        checkPagination(0);
    }

    private void checkPagination(final long nbRecords) {
        final Pagination<Tag> foundTags = tagUserApi.searchTags("ACCOUNT", 0L, nbRecords + 1L, callContext);
        Assert.assertEquals(foundTags.iterator().hasNext(), nbRecords > 0);
        Assert.assertEquals(foundTags.getMaxNbRecords(), (Long) nbRecords);
        Assert.assertEquals(foundTags.getTotalNbRecords(), (Long) nbRecords);

        final Pagination<Tag> gotTags = tagUserApi.getTags(0L, nbRecords + 1L, callContext);
        Assert.assertEquals(gotTags.iterator().hasNext(), nbRecords > 0);
        Assert.assertEquals(gotTags.getMaxNbRecords(), (Long) nbRecords);
        Assert.assertEquals(gotTags.getTotalNbRecords(), (Long) nbRecords);
    }
}
