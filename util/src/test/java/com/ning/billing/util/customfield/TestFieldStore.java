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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.MockClockModule;
import com.ning.billing.util.customfield.dao.AuditedCustomFieldDao;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.glue.FieldStoreModule;
import org.apache.commons.io.IOUtils;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.customfield.dao.CustomFieldSqlDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test(groups = {"util", "slow"})
public class TestFieldStore {
    Logger log = LoggerFactory.getLogger(TestFieldStore.class);
    private final MysqlTestingHelper helper = new MysqlTestingHelper();
    private CallContext context;
    private IDBI dbi;
    private CustomFieldDao customFieldDao;

    @BeforeClass(groups = {"util", "slow"})
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            final String utilDdl = IOUtils.toString(TestFieldStore.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

            helper.startMysql();
            helper.initDb(utilDdl);

            dbi = helper.getDBI();
            customFieldDao = new AuditedCustomFieldDao();

            FieldStoreModule module = new FieldStoreModule();
            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module, new MockClockModule());
            Clock clock = injector.getInstance(Clock.class);
            context = new DefaultCallContextFactory(clock).createCallContext("Fezzik", CallOrigin.TEST, UserType.TEST);

        }
        catch (Throwable t) {
            log.error("Setup failed", t);
            fail(t.toString());
        }
    }

    @AfterClass(groups = {"util", "slow"})
    public void stopMysql()
    {
        if (helper!= null) {
            helper.stopMysql();
        }
    }

    @Test
    public void testFieldStore() {
        final UUID id = UUID.randomUUID();
        final String objectType = "Test widget";

        final FieldStore fieldStore1 = new DefaultFieldStore(id, objectType);

        String fieldName = "TestField1";
        String fieldValue = "Kitty Hawk";
        fieldStore1.setValue(fieldName, fieldValue);

        CustomFieldSqlDao customFieldSqlDao = dbi.onDemand(CustomFieldSqlDao.class);
        customFieldDao.saveFields(customFieldSqlDao, id, objectType, fieldStore1.getEntityList(), context);

        final FieldStore fieldStore2 = DefaultFieldStore.create(id, objectType);
        fieldStore2.add(customFieldSqlDao.load(id.toString(), objectType));

        assertEquals(fieldStore2.getValue(fieldName), fieldValue);

        fieldValue = "Cape Canaveral";
        fieldStore2.setValue(fieldName, fieldValue);
        assertEquals(fieldStore2.getValue(fieldName), fieldValue);
        customFieldDao.saveFields(customFieldSqlDao, id, objectType, fieldStore2.getEntityList(), context);

        final FieldStore fieldStore3 = DefaultFieldStore.create(id, objectType);
        assertEquals(fieldStore3.getValue(fieldName), null);
        fieldStore3.add(customFieldSqlDao.load(id.toString(), objectType));

        assertEquals(fieldStore3.getValue(fieldName), fieldValue);
    }
}
