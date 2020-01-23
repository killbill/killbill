/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.util.customfield.dao;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.entity.Pagination;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDefaultCustomFieldDao extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testSearchByObjectTypeAndFields() throws CustomFieldApiException {

        final UUID someUUId = UUID.randomUUID();
        final String SEARCHABLE_FILED_NAME = "ToBeSearched";
        final String NON_SEARCHABLE_FILED_NAME = "NotToBeFound";

        final List<CustomFieldModelDao> input = ImmutableList.of(
                new CustomFieldModelDao(internalCallContext.getCreatedDate(), SEARCHABLE_FILED_NAME, "The world will collapse soon!", someUUId, ObjectType.ACCOUNT),
                new CustomFieldModelDao(internalCallContext.getCreatedDate(), SEARCHABLE_FILED_NAME, "How do you know?", someUUId, ObjectType.ACCOUNT),
                new CustomFieldModelDao(internalCallContext.getCreatedDate(), SEARCHABLE_FILED_NAME, "Just a guess...", someUUId, ObjectType.INVOICE),
                new CustomFieldModelDao(internalCallContext.getCreatedDate(), NON_SEARCHABLE_FILED_NAME, "Are you a psychic???", someUUId, ObjectType.ACCOUNT),
                new CustomFieldModelDao(internalCallContext.getCreatedDate(), NON_SEARCHABLE_FILED_NAME, "Yes!!!!", someUUId, ObjectType.INVOICE));

        eventsListener.pushExpectedEvents(NextEvent.CUSTOM_FIELD, NextEvent.CUSTOM_FIELD, NextEvent.CUSTOM_FIELD, NextEvent.CUSTOM_FIELD, NextEvent.CUSTOM_FIELD);
        ((DefaultCustomFieldDao) customFieldDao).create(input, internalCallContext);
        eventsListener.assertListenerStatus();

        Pagination<CustomFieldModelDao> result = customFieldDao.searchCustomFields(SEARCHABLE_FILED_NAME, ObjectType.INVOICE, 0L, 100L, internalCallContext);
        Assert.assertEquals(result.getTotalNbRecords().longValue(), 1L);
        Assert.assertEquals(result.getMaxNbRecords().longValue(), 5L);
        CustomFieldModelDao nextCustomField = result.iterator().next();
        Assert.assertEquals(nextCustomField.getObjectType(), ObjectType.INVOICE);
        Assert.assertEquals(nextCustomField.getFieldName(), SEARCHABLE_FILED_NAME);


        result = customFieldDao.searchCustomFields(SEARCHABLE_FILED_NAME, ObjectType.ACCOUNT, 0L, 100L, internalCallContext);
        Assert.assertEquals(result.getTotalNbRecords().longValue(), 2L);
        Assert.assertEquals(result.getMaxNbRecords().longValue(), 5L);

        nextCustomField = result.iterator().next();
        Assert.assertEquals(nextCustomField.getObjectType(), ObjectType.ACCOUNT);
        Assert.assertEquals(nextCustomField.getFieldName(), SEARCHABLE_FILED_NAME);

        nextCustomField = result.iterator().next();
        Assert.assertEquals(nextCustomField.getObjectType(), ObjectType.ACCOUNT);
        Assert.assertEquals(nextCustomField.getFieldName(), SEARCHABLE_FILED_NAME);


        result = customFieldDao.searchCustomFields(SEARCHABLE_FILED_NAME, "The world will collapse soon!", ObjectType.ACCOUNT, 0L, 100L, internalCallContext);
        Assert.assertEquals(result.getTotalNbRecords().longValue(), 1L);
        Assert.assertEquals(result.getMaxNbRecords().longValue(), 5L);

        nextCustomField = result.iterator().next();
        Assert.assertEquals(nextCustomField.getObjectType(), ObjectType.ACCOUNT);
        Assert.assertEquals(nextCustomField.getFieldName(), SEARCHABLE_FILED_NAME);
        Assert.assertEquals(nextCustomField.getFieldValue(), "The world will collapse soon!");


        result = customFieldDao.searchCustomFields(NON_SEARCHABLE_FILED_NAME, "The world will collapse soon!", ObjectType.ACCOUNT, 0L, 100L, internalCallContext);
        Assert.assertEquals(result.getTotalNbRecords().longValue(), 0L);
        Assert.assertEquals(result.getMaxNbRecords().longValue(), 5L);


    }

}
