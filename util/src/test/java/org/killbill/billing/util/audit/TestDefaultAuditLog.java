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

package org.killbill.billing.util.audit;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.clock.ClockMock;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.dao.EntityAudit;
import org.killbill.billing.util.dao.TableName;

public class TestDefaultAuditLog extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testGetters() throws Exception {
        final TableName tableName = TableName.ACCOUNT_EMAIL_HISTORY;
        final long recordId = Long.MAX_VALUE;
        final ChangeType changeType = ChangeType.DELETE;
        final EntityAudit entityAudit = new EntityAudit(tableName, recordId, changeType, null);

        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();
        final String userName = UUID.randomUUID().toString();
        final CallOrigin callOrigin = CallOrigin.EXTERNAL;
        final UserType userType = UserType.CUSTOMER;
        final UUID userToken = UUID.randomUUID();
        final ClockMock clock = new ClockMock();
        final CallContext callContext = new DefaultCallContext(accountId, tenantId, userName, callOrigin, userType, userToken, clock);

        final AuditLog auditLog = new DefaultAuditLog(new AuditLogModelDao(entityAudit, callContext), ObjectType.ACCOUNT_EMAIL, UUID.randomUUID());
        Assert.assertEquals(auditLog.getChangeType(), changeType);
        Assert.assertNull(auditLog.getComment());
        Assert.assertNotNull(auditLog.getCreatedDate());
        Assert.assertNull(auditLog.getReasonCode());
        Assert.assertEquals(auditLog.getUserName(), userName);
        Assert.assertEquals(auditLog.getUserToken(), userToken.toString());
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final TableName tableName = TableName.ACCOUNT_EMAIL_HISTORY;
        final long recordId = Long.MAX_VALUE;
        final ChangeType changeType = ChangeType.DELETE;
        final EntityAudit entityAudit = new EntityAudit(tableName, recordId, changeType, null);

        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();
        final String userName = UUID.randomUUID().toString();
        final CallOrigin callOrigin = CallOrigin.EXTERNAL;
        final UserType userType = UserType.CUSTOMER;
        final UUID userToken = UUID.randomUUID();
        final ClockMock clock = new ClockMock();
        final CallContext callContext = new DefaultCallContext(accountId, tenantId, userName, callOrigin, userType, userToken, clock);

        final AuditLogModelDao auditLog = new AuditLogModelDao(entityAudit, callContext);
        Assert.assertEquals(auditLog, auditLog);

        final AuditLogModelDao sameAuditLog = new AuditLogModelDao(entityAudit, callContext);
        Assert.assertEquals(sameAuditLog, auditLog);

        clock.addMonths(1);
        final CallContext otherCallContext = new DefaultCallContext(accountId, tenantId, userName, callOrigin, userType, userToken, clock);
        final AuditLogModelDao otherAuditLog = new AuditLogModelDao(entityAudit, otherCallContext);
        Assert.assertNotEquals(otherAuditLog, auditLog);
    }
}
