/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.util.customfield;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.customfield.dao.CustomFieldModelDao;
import org.testng.annotations.Test;

public class TestFieldStore extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCreateCustomField() throws CustomFieldApiException {
        final UUID id = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT;

        String fieldName = "TestField1";
        String fieldValue = "Kitty Hawk";

        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldDao.create(new CustomFieldModelDao(internalCallContext.getCreatedDate(), fieldName, fieldValue, id, objectType), internalCallContext);
        assertListenerStatus();

        fieldName = "TestField2";
        fieldValue = "Cape Canaveral";
        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldDao.create(new CustomFieldModelDao(internalCallContext.getCreatedDate(), fieldName, fieldValue, id, objectType), internalCallContext);
        assertListenerStatus();
    }
}
