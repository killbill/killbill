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

package com.ning.billing.util.audit;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.UtilTestSuite;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.TableName;

public class TestDefaultAuditLog extends UtilTestSuite {

    @Test(groups = "fast")
    public void testGetters() throws Exception {
        final TableName tableName = TableName.ACCOUNT_EMAIL_HISTORY;
        final long recordId = Long.MAX_VALUE;
        final ChangeType changeType = ChangeType.DELETE;
        final EntityAudit entityAudit = new EntityAudit(tableName, recordId, changeType);

        final String userName = UUID.randomUUID().toString();
        final CallOrigin callOrigin = CallOrigin.EXTERNAL;
        final UserType userType = UserType.CUSTOMER;
        final UUID userToken = UUID.randomUUID();
        final ClockMock clock = new ClockMock();
        final CallContext callContext = new DefaultCallContext(userName, callOrigin, userType, userToken, clock);

        final AuditLog auditLog = new DefaultAuditLog(entityAudit, callContext);
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
        final EntityAudit entityAudit = new EntityAudit(tableName, recordId, changeType);

        final String userName = UUID.randomUUID().toString();
        final CallOrigin callOrigin = CallOrigin.EXTERNAL;
        final UserType userType = UserType.CUSTOMER;
        final UUID userToken = UUID.randomUUID();
        final ClockMock clock = new ClockMock();
        final CallContext callContext = new DefaultCallContext(userName, callOrigin, userType, userToken, clock);

        final AuditLog auditLog = new DefaultAuditLog(entityAudit, callContext);
        Assert.assertEquals(auditLog, auditLog);

        final AuditLog sameAuditLog = new DefaultAuditLog(entityAudit, callContext);
        Assert.assertEquals(sameAuditLog, auditLog);

        clock.addMonths(1);
        final CallContext otherCallContext = new DefaultCallContext(userName, callOrigin, userType, userToken, clock);
        final AuditLog otherAuditLog = new DefaultAuditLog(entityAudit, otherCallContext);
        Assert.assertNotEquals(otherAuditLog, auditLog);
    }
}
