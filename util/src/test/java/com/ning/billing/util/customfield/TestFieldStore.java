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

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.customfield.dao.AuditedCustomFieldDao;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.customfield.dao.CustomFieldSqlDao;
import com.ning.billing.util.dao.ObjectType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestFieldStore extends UtilTestSuiteWithEmbeddedDB {
    private final Logger log = LoggerFactory.getLogger(TestFieldStore.class);
    private final MysqlTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getMysqlTestingHelper();
    private IDBI dbi;
    private CustomFieldDao customFieldDao;

    @BeforeClass(groups = "slow")
    protected void setup() throws IOException {
        try {
            dbi = helper.getDBI();
            customFieldDao = new AuditedCustomFieldDao(dbi);
        } catch (Throwable t) {
            log.error("Setup failed", t);
            fail(t.toString());
        }
    }

    @Test(groups = "slow")
    public void testFieldStore() {
        final UUID id = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT;

        final FieldStore fieldStore1 = new DefaultFieldStore(id, objectType);

        final String fieldName = "TestField1";
        String fieldValue = "Kitty Hawk";
        fieldStore1.setValue(fieldName, fieldValue);

        final CustomFieldSqlDao customFieldSqlDao = dbi.onDemand(CustomFieldSqlDao.class);
        customFieldDao.saveEntitiesFromTransaction(customFieldSqlDao, id, objectType, fieldStore1.getEntityList(), internalCallContext);

        final FieldStore fieldStore2 = DefaultFieldStore.create(id, objectType);
        fieldStore2.add(customFieldSqlDao.load(id.toString(), objectType, internalCallContext));

        assertEquals(fieldStore2.getValue(fieldName), fieldValue);

        fieldValue = "Cape Canaveral";
        fieldStore2.setValue(fieldName, fieldValue);
        assertEquals(fieldStore2.getValue(fieldName), fieldValue);
        customFieldDao.saveEntitiesFromTransaction(customFieldSqlDao, id, objectType, fieldStore2.getEntityList(), internalCallContext);

        final FieldStore fieldStore3 = DefaultFieldStore.create(id, objectType);
        assertEquals(fieldStore3.getValue(fieldName), null);
        fieldStore3.add(customFieldSqlDao.load(id.toString(), objectType, internalCallContext));

        assertEquals(fieldStore3.getValue(fieldName), fieldValue);
    }
}
