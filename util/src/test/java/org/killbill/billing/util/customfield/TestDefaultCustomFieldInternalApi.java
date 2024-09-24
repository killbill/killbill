/*
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.util.customfield;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.customfield.dao.CustomFieldModelDao;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TestDefaultCustomFieldInternalApi extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testSearchUniqueCustomField() throws CustomFieldApiException {
        final UUID accountId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT;

        final String fieldName = "TestField1";
        final String fieldValue = "1"; // Choosing a number on purpose (verify typing in SearchQuery)

        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        final CustomFieldModelDao customFieldModelDao = new CustomFieldModelDao(internalCallContext.getCreatedDate(), fieldName, fieldValue, accountId, objectType);
        customFieldDao.create(customFieldModelDao, internalCallContext);
        assertListenerStatus();

        CustomField customField = customFieldInternalApi.searchUniqueCustomField(fieldName, fieldValue, objectType, internalCallContext);
        Assert.assertNotNull(customField);
        Assert.assertEquals(customField.getId(), customFieldModelDao.getId());

        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldDao.deleteCustomFields(List.of(customField.getId()), internalCallContext);
        assertListenerStatus();

        customField = customFieldInternalApi.searchUniqueCustomField(fieldName, fieldValue, objectType, internalCallContext);
        Assert.assertNull(customField);
    }
}
