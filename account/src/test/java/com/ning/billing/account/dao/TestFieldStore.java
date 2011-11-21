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

package com.ning.billing.account.dao;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.account.api.FieldStore;
import com.ning.billing.account.glue.AccountModuleMock;
import com.ning.billing.account.glue.InjectorMagic;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test(groups={"Account-DAO"})
public class TestFieldStore {
    private InjectorMagic injectorMagic;

    @BeforeClass(alwaysRun = true)
    private void setup() throws IOException {
        AccountModuleMock module = new AccountModuleMock();
        final String ddl = IOUtils.toString(IAccountDaoSql.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        module.createDb(ddl);

        // Healthcheck test to make sure MySQL is setup properly
        try {
            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);
            injectorMagic = injector.getInstance(InjectorMagic.class);

            IFieldStoreDao dao = injector.getInstance(IFieldStoreDao.class);
            dao.test();
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }

    @Test
    public void testFieldStore() {
        UUID id = UUID.randomUUID();
        String objectType = "Test widget";

        FieldStore fieldStore = new FieldStore(id, objectType);

        String fieldName = "TestField1";
        String fieldValue = "Kitty Hawk";
        fieldStore.setValue(fieldName, fieldValue);

        fieldStore.save();

        fieldStore = FieldStore.create(id, objectType);
        fieldStore.load();

        assertEquals(fieldStore.getValue(fieldName), fieldValue);
    }
}
