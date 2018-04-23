/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs.json;

import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.audit.DefaultAuditLog;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;
import org.killbill.billing.util.dao.EntityAudit;
import org.killbill.billing.util.dao.TableName;

public class TestAuditLogJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String changeType = UUID.randomUUID().toString();
        final DateTime changeDate = clock.getUTCNow();
        final String changedBy = UUID.randomUUID().toString();
        final String reasonCode = UUID.randomUUID().toString();
        final String comments = UUID.randomUUID().toString();
        final String userToken = UUID.randomUUID().toString();
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.BUNDLE;

        final AuditLogJson auditLogJson = new AuditLogJson(changeType, changeDate, objectType, objectId, changedBy, reasonCode, comments, userToken, null);
        Assert.assertEquals(auditLogJson.getChangeType(), changeType);
        Assert.assertEquals(auditLogJson.getChangeDate(), changeDate);
        Assert.assertEquals(auditLogJson.getChangedBy(), changedBy);
        Assert.assertEquals(auditLogJson.getReasonCode(), reasonCode);
        Assert.assertEquals(auditLogJson.getComments(), comments);
        Assert.assertEquals(auditLogJson.getUserToken(), userToken);
        Assert.assertEquals(auditLogJson.getObjectType(), objectType);
        Assert.assertEquals(auditLogJson.getObjectId(), objectId);


        final String asJson = mapper.writeValueAsString(auditLogJson);
        Assert.assertEquals(asJson, "{\"changeType\":\"" + auditLogJson.getChangeType() + "\"," +
                                    "\"changeDate\":\"" + auditLogJson.getChangeDate().toDateTimeISO().toString() + "\"," +
                                    "\"objectType\":\"" + auditLogJson.getObjectType().toString() + "\"," +
                                    "\"objectId\":\"" + auditLogJson.getObjectId().toString() + "\"," +
                                    "\"changedBy\":\"" + auditLogJson.getChangedBy() + "\"," +
                                    "\"reasonCode\":\"" + auditLogJson.getReasonCode() + "\"," +
                                    "\"comments\":\"" + auditLogJson.getComments() + "\"," +
                                    "\"userToken\":\"" + auditLogJson.getUserToken() + "\"," +
                                    "\"history\":" + auditLogJson.getHistory() + "}");

        final AuditLogJson fromJson = mapper.readValue(asJson, AuditLogJson.class);
        Assert.assertEquals(fromJson, auditLogJson);
    }

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final TableName tableName = TableName.ACCOUNT_EMAIL_HISTORY;
        final long recordId = Long.MAX_VALUE;
        final ChangeType changeType = ChangeType.DELETE;
        final EntityAudit entityAudit = new EntityAudit(tableName, recordId, changeType, null);

        final AuditLog auditLog = new DefaultAuditLog(new AuditLogModelDao(entityAudit, callContext), ObjectType.ACCOUNT_EMAIL, UUID.randomUUID());

        final AuditLogJson auditLogJson = new AuditLogJson(auditLog);
        Assert.assertEquals(auditLogJson.getChangeType(), changeType.toString());
        Assert.assertNotNull(auditLogJson.getChangeDate());
        Assert.assertEquals(auditLogJson.getChangedBy(), callContext.getUserName());
        Assert.assertEquals(auditLogJson.getReasonCode(), callContext.getReasonCode());
        Assert.assertEquals(auditLogJson.getComments(), callContext.getComments());
        Assert.assertEquals(auditLogJson.getUserToken(), callContext.getUserToken().toString());
        Assert.assertEquals(auditLogJson.getObjectType(), ObjectType.ACCOUNT_EMAIL);
    }
}
