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

import java.io.IOException;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.customfield.dao.CustomFieldModelDao;
import com.ning.billing.util.customfield.dao.DefaultCustomFieldDao;
import com.ning.billing.util.dao.DefaultNonEntityDao;
import com.ning.billing.util.dao.NonEntityDao;

import static org.testng.Assert.fail;

public class TestFieldStore extends UtilTestSuiteWithEmbeddedDB {

    private final Logger log = LoggerFactory.getLogger(TestFieldStore.class);
    private CustomFieldDao customFieldDao;

    private CacheControllerDispatcher controllerDispatcher = new CacheControllerDispatcher();

    @BeforeClass(groups = "slow")
    protected void setup() throws IOException {
        try {
            final IDBI dbi = getDBI();
            final NonEntityDao nonEntityDao = new DefaultNonEntityDao(dbi);
            customFieldDao = new DefaultCustomFieldDao(dbi, clock, controllerDispatcher, nonEntityDao);
        } catch (Throwable t) {
            log.error("Setup failed", t);
            fail(t.toString());
        }
    }

    @Test(groups = "slow")
    public void testCreateCustomField() throws CustomFieldApiException {
        final UUID id = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT;

        String fieldName = "TestField1";
        String fieldValue = "Kitty Hawk";

        final CustomField field = new StringCustomField(fieldName, fieldValue, objectType, id, internalCallContext.getCreatedDate());
        customFieldDao.create(new CustomFieldModelDao(field), internalCallContext);

        fieldName = "TestField2";
        fieldValue = "Cape Canaveral";
        final CustomField field2 = new StringCustomField(fieldName, fieldValue, objectType, id, internalCallContext.getCreatedDate());
        customFieldDao.create(new CustomFieldModelDao(field2), internalCallContext);
    }
}
