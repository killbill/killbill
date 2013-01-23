/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.callcontext;

import java.util.Date;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.dao.DefaultNonEntityDao;
import com.ning.billing.util.dao.NonEntityDao;

public class TestInternalCallContextFactory extends UtilTestSuiteWithEmbeddedDB {

    private InternalCallContextFactory internalCallContextFactory;
    private CacheControllerDispatcher cacheControllerDispatcher;
    private NonEntityDao nonEntityDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        cacheControllerDispatcher =  new CacheControllerDispatcher();
        nonEntityDao = new DefaultNonEntityDao(getDBI());
        internalCallContextFactory = new InternalCallContextFactory(new ClockMock(), nonEntityDao, cacheControllerDispatcher);
    }

    @Test(groups = "slow")
    public void testCreateInternalCallContextWithAccountRecordIdFromSimpleObjectType() throws Exception {
        final UUID invoiceId = UUID.randomUUID();
        final Long accountRecordId = 19384012L;

        getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("DROP TABLE IF EXISTS invoices;\n" +
                               "CREATE TABLE invoices (\n" +
                               "    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,\n" +
                               "    id char(36) NOT NULL,\n" +
                               "    account_id char(36) NOT NULL,\n" +
                               "    invoice_date date NOT NULL,\n" +
                               "    target_date date NOT NULL,\n" +
                               "    currency char(3) NOT NULL,\n" +
                               "    migrated bool NOT NULL,\n" +
                               "    created_by varchar(50) NOT NULL,\n" +
                               "    created_date datetime NOT NULL,\n" +
                               "    account_record_id int(11) unsigned default null,\n" +
                               "    tenant_record_id int(11) unsigned default null,\n" +
                               "    PRIMARY KEY(record_id)\n" +
                               ");");
                handle.execute("insert into invoices (id, account_id, invoice_date, target_date, currency, migrated, created_by, created_date, account_record_id) values " +
                               "(?, ?, now(), now(), 'USD', 0, 'test', now(), ?)", invoiceId.toString(), UUID.randomUUID().toString(), accountRecordId);
                return null;
            }
        });

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(invoiceId, ObjectType.INVOICE, callContext);
        // The account record id should have been looked up in the invoices table
        Assert.assertEquals(context.getAccountRecordId(), accountRecordId);
        verifyInternalCallContext(context);
    }

    @Test(groups = "slow")
    public void testCreateInternalCallContextWithAccountRecordIdFromAccountObjectType() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final Long accountRecordId = 19384012L;

        getDBTestingHelper().getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into accounts (record_id, id, email, name, first_name_length, is_notified_for_invoices, created_date, created_by, updated_date, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               accountRecordId, accountId.toString(), "yo@t.com", "toto", 4, false, new Date(), "i", new Date(), "j");
                return null;
            }
        });

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, ObjectType.ACCOUNT, callContext);
        // The account record id should have been looked up in the accounts table
        Assert.assertEquals(context.getAccountRecordId(), accountRecordId);
        verifyInternalCallContext(context);
    }

    private void verifyInternalCallContext(final InternalCallContext context) {
        Assert.assertEquals(context.getCallOrigin(), callContext.getCallOrigin());
        Assert.assertEquals(context.getComments(), callContext.getComments());
        Assert.assertEquals(context.getCreatedDate(), callContext.getCreatedDate());
        Assert.assertEquals(context.getReasonCode(), callContext.getReasonCode());
        Assert.assertEquals(context.getUpdatedDate(), callContext.getUpdatedDate());
        Assert.assertEquals(context.getCreatedBy(), callContext.getUserName());
        Assert.assertEquals(context.getUserToken(), callContext.getUserToken());
        Assert.assertEquals(context.getContextUserType(), callContext.getUserType());
        // Our test callContext doesn't have a tenant id
        Assert.assertEquals(context.getTenantRecordId(), (Long) InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID);
    }
}
