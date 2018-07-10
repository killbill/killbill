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

package org.killbill.billing.util.dao;

import java.util.List;

import org.killbill.billing.ObjectType;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.tag.dao.TagDefinitionModelDao;
import org.killbill.billing.util.tag.dao.TagDefinitionSqlDao;

import com.google.common.collect.ImmutableList;

public class TestPagination extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "Test Pagination: basic SqlDAO and DAO calls")
    public void testTagDefinitionsPagination() throws Exception {
        final TagDefinitionSqlDao tagDefinitionSqlDao = dbi.onDemand(TagDefinitionSqlDao.class);

        for (int i = 0; i < 10; i++) {
            final String definitionName = "name-" + i;
            final String description = "description-" + i;
            eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
            tagDefinitionDao.create(definitionName, description, ObjectType.ACCOUNT.name(), internalCallContext);
            assertListenerStatus();
        }

        // Tests via SQL dao directly
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionSqlDao.getAll(internalCallContext)).size(), 10);
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionSqlDao.get(0L, 100L, "record_id", "asc", internalCallContext)).size(), 10);
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionSqlDao.get(5L, 100L, "record_id", "asc", internalCallContext)).size(), 5);
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionSqlDao.get(5L, 10L, "record_id", "asc", internalCallContext)).size(), 5);
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionSqlDao.get(0L, 5L, "record_id", "asc", internalCallContext)).size(), 5);
        for (int i = 0; i < 10; i++) {
            final List<TagDefinitionModelDao> tagDefinitions = ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionSqlDao.get(0L, (long) i, "record_id", "asc", internalCallContext));
            Assert.assertEquals(tagDefinitions.size(), i);

            for (int j = 0; j < tagDefinitions.size(); j++) {
                Assert.assertEquals(tagDefinitions.get(j).getName(), "name-" + j);
                Assert.assertEquals(tagDefinitions.get(j).getDescription(), "description-" + j);
            }
        }

        // Tests via DAO (to test EntityDaoBase)
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionDao.getAll(internalCallContext)).size(), 10);
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionDao.get(0L, 100L, internalCallContext)).size(), 10);
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionDao.get(5L, 100L, internalCallContext)).size(), 5);
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionDao.get(5L, 10L, internalCallContext)).size(), 5);
        Assert.assertEquals(ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionDao.get(0L, 5L, internalCallContext)).size(), 5);
        for (int i = 0; i < 10; i++) {
            final List<TagDefinitionModelDao> tagDefinitions = ImmutableList.<TagDefinitionModelDao>copyOf(tagDefinitionDao.get(0L, (long) i, internalCallContext));
            Assert.assertEquals(tagDefinitions.size(), i);

            for (int j = 0; j < tagDefinitions.size(); j++) {
                Assert.assertEquals(tagDefinitions.get(j).getName(), "name-" + j);
                Assert.assertEquals(tagDefinitions.get(j).getDescription(), "description-" + j);
            }
        }
    }
}
