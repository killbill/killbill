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

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.account.glue.AccountModule;
import com.ning.billing.account.glue.AccountModuleMock;

public class TestSimpleAccountDao {


    private IAccountDao dao;

    public static void loadSystemPropertiesFromClasspath( final String resource) {
        final URL url = TestSimpleAccountDao.class.getResource(resource);
        assertNotNull(url);

        try {
            System.getProperties().load( url.openStream() );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private  Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new AccountModuleMock());
    }


    @BeforeClass(groups={"setup"})
    public void setup() {
        //loadSystemPropertiesFromClasspath("/account.properties");
        final Injector g = getInjector();

        dao = g.getInstance(IAccountDao.class);
    }

    @Test(enabled=true, groups={"sql"})
    public void testBasic() {

        IAccount a = new Account("foo");
        dao.createAccount(a);

        IAccount r = dao.getAccountByKey("foo");
        assertNotNull(r);
        assertEquals(r.getId(), a.getId());
        assertEquals(r.getKey(), a.getKey());

        r = dao.getAccountFromId(a.getId());
        assertNotNull(r);
        assertEquals(r.getId(), a.getId());
        assertEquals(r.getKey(), a.getKey());

        List<IAccount> all = dao.getAccounts();
        assertNotNull(all);
        assertEquals(all.size(), 1);
    }


}
