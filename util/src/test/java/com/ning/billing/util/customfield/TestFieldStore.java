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

package com.ning.billing.util.customfield;

import java.util.UUID;

import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.customfield.dao.CustomFieldModelDao;

public class TestFieldStore extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCreateCustomField() throws CustomFieldApiException {
        final UUID id = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT;

        String fieldName = "TestField1";
        String fieldValue = "Kitty Hawk";

        final CustomField field = new StringCustomField(fieldName, fieldValue, objectType, id, internalCallContext.getCreatedDate());
        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldDao.create(new CustomFieldModelDao(field), internalCallContext);
        assertListenerStatus();

        fieldName = "TestField2";
        fieldValue = "Cape Canaveral";
        final CustomField field2 = new StringCustomField(fieldName, fieldValue, objectType, id, internalCallContext.getCreatedDate());
        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldDao.create(new CustomFieldModelDao(field2), internalCallContext);
        assertListenerStatus();
    }
}
