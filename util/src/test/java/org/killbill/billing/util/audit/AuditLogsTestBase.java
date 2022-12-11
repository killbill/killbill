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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.mockito.Mockito;

public abstract class AuditLogsTestBase extends UtilTestSuiteNoDB {

    protected Map<UUID, List<AuditLog>> createAuditLogsAssociation() {
        final UUID id1 = UUID.randomUUID();
        final UUID id2 = UUID.randomUUID();
        final UUID id3 = UUID.randomUUID();
        return Map.of(id1, List.of(createAuditLog(), createAuditLog()),
                      id2, List.of(createAuditLog(), createAuditLog()),
                      id3, List.of(createAuditLog(), createAuditLog()));
    }

    protected AuditLog createAuditLog() {
        final AuditLog auditLog = Mockito.mock(AuditLog.class);
        final DateTime utcNow = clock.getUTCNow();
        Mockito.when(auditLog.getCreatedDate()).thenReturn(utcNow);
        Mockito.when(auditLog.getReasonCode()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getUserName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getUserToken()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getComment()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getChangeType()).thenReturn(ChangeType.DELETE);

        return auditLog;
    }
}
