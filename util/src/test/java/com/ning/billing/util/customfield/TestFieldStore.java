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
import org.apache.commons.io.IOUtils;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.customfield.DefaultFieldStore;
import com.ning.billing.util.customfield.FieldStore;
import com.ning.billing.util.customfield.dao.FieldStoreDao;
import com.ning.billing.util.eventbus.DefaultEventBusService;
import com.ning.billing.util.eventbus.EventBusService;
import com.ning.billing.util.glue.EventBusModule;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test(groups={"util"})
public class TestFieldStore {
    Logger log = LoggerFactory.getLogger(TestFieldStore.class);
    private final MysqlTestingHelper helper = new MysqlTestingHelper();
    private IDBI dbi;

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            final String utilDdl = IOUtils.toString(TestFieldStore.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

            helper.startMysql();
            helper.initDb(utilDdl);

            dbi = helper.getDBI();
        }
        catch (Throwable t) {
            log.error("Setup failed", t);
            fail(t.toString());
        }
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql()
    {
        helper.stopMysql();
    }

    @Test
    public void testFieldStore() {
        UUID id = UUID.randomUUID();
        String objectType = "Test widget";

        FieldStore fieldStore = new DefaultFieldStore(id, objectType);

        String fieldName = "TestField1";
        String fieldValue = "Kitty Hawk";
        fieldStore.setValue(fieldName, fieldValue);

        FieldStoreDao fieldStoreDao = dbi.onDemand(FieldStoreDao.class);
        fieldStoreDao.save(id.toString(), objectType, fieldStore.getEntityList());

        fieldStore = DefaultFieldStore.create(id, objectType);
        fieldStore.add(fieldStoreDao.load(id.toString(), objectType));

        assertEquals(fieldStore.getValue(fieldName), fieldValue);

        fieldValue = "Cape Canaveral";
        fieldStore.setValue(fieldName, fieldValue);
        assertEquals(fieldStore.getValue(fieldName), fieldValue);
        fieldStoreDao.save(id.toString(), objectType, fieldStore.getEntityList());

        fieldStore = DefaultFieldStore.create(id, objectType);
        assertEquals(fieldStore.getValue(fieldName), null);
        fieldStore.add(fieldStoreDao.load(id.toString(), objectType));

        assertEquals(fieldStore.getValue(fieldName), fieldValue);
    }
}
