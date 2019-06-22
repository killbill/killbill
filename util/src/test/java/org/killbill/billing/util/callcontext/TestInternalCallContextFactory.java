/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.callcontext;

import java.util.Date;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.mockito.Mockito;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Update;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.LongMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInternalCallContextFactory extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCreateInternalCallContextWithAccountRecordIdFromSimpleObjectType() throws Exception {
        final UUID invoiceId = UUID.randomUUID();
        final Long accountRecordId = 19384012L;

        final ImmutableAccountData immutableAccountData = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(immutableAccountInternalApi.getImmutableAccountDataByRecordId(Mockito.<Long>eq(accountRecordId), Mockito.<InternalTenantContext>any())).thenReturn(immutableAccountData);

        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("insert into invoices (id, account_id, invoice_date, target_date, currency, status, migrated, created_by, created_date, account_record_id, tenant_record_id) values " +
                               "(?, ?, ?, ?, 'USD', 'COMMITTED', '0', 'test', ?, ?, ?)", invoiceId.toString(), UUID.randomUUID().toString(), new Date(), new Date(), new Date(), accountRecordId, internalCallContext.getTenantRecordId());
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

        final Long accountRecordId = dbi.withHandle(new HandleCallback<Long>() {
            @Override
            public Long withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                return update(handle,
                              "insert into accounts (id, external_key, email, name, first_name_length, reference_time, time_zone, created_date, created_by, updated_date, updated_by, tenant_record_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                              accountId.toString(), accountId.toString(), "yo@t.com", "toto", 4, new Date(), "UTC", new Date(), "i", new Date(), "j", internalCallContext.getTenantRecordId());
            }

            Long update(final Handle handle, final String sql, final Object... args) {
                final Update stmt = handle.createStatement(sql);
                int position = 0;
                for (final Object arg : args) {
                    stmt.bind(position++, arg);
                }
                return stmt.executeAndReturnGeneratedKeys(new LongMapper(), "record_id").first();
            }
        });

        final ImmutableAccountData immutableAccountData = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(immutableAccountInternalApi.getImmutableAccountDataByRecordId(Mockito.<Long>eq(accountRecordId), Mockito.<InternalTenantContext>any())).thenReturn(immutableAccountData);

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, ObjectType.ACCOUNT, callContext);
        // The account record id should have been looked up in the accounts table
        Assert.assertEquals(context.getAccountRecordId(), accountRecordId);
        verifyInternalCallContext(context);
    }

    private void verifyInternalCallContext(final InternalCallContext context) {
        Assert.assertEquals(context.getCallOrigin(), callContext.getCallOrigin());
        Assert.assertEquals(context.getComments(), callContext.getComments());
        Assert.assertTrue(context.getCreatedDate().compareTo(callContext.getCreatedDate()) >= 0);
        Assert.assertEquals(context.getReasonCode(), callContext.getReasonCode());
        Assert.assertTrue(context.getUpdatedDate().compareTo(callContext.getUpdatedDate()) >= 0);
        Assert.assertEquals(context.getCreatedBy(), callContext.getUserName());
        Assert.assertEquals(context.getUserToken(), callContext.getUserToken());
        Assert.assertEquals(context.getContextUserType(), callContext.getUserType());
        // Our test callcontext doesn't have a tenant id
        Assert.assertEquals(context.getTenantRecordId(), (Long) InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID);
    }
}
