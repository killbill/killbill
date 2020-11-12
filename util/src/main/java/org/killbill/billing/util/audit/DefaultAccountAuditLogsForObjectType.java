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

package org.killbill.billing.util.audit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.customfield.ShouldntHappenException;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;

public class DefaultAccountAuditLogsForObjectType implements AccountAuditLogsForObjectType {

    private final Map<UUID, List<AuditLog>> auditLogsCache;

    private final AuditLevel auditLevel;
    private final Iterator<AuditLog> allAuditLogsForObjectType;

    public DefaultAccountAuditLogsForObjectType(final AuditLevel auditLevel) {
        this(auditLevel, ImmutableSet.<AuditLog>of().iterator());
    }

    public DefaultAccountAuditLogsForObjectType(final AuditLevel auditLevel, final Iterator<AuditLog> allAuditLogsForObjectType) {
        this.auditLevel = auditLevel;
        this.auditLogsCache = new HashMap<UUID, List<AuditLog>>();
        this.allAuditLogsForObjectType = allAuditLogsForObjectType;
    }

    // Used by DefaultAccountAuditLogs
    void initializeIfNeeded(final UUID objectId) {
        if (auditLogsCache.get(objectId) == null) {
            auditLogsCache.put(objectId, new LinkedList<AuditLog>());
        }
    }

    @Override
    public List<AuditLog> getAuditLogs(final UUID objectId) {

        // Loop through the whole list to make sure we don't leak any connections
        // and cache AuditLog based on AuditLevel
        cacheAllAuditLogs();

        // We went through the whole list, mark we don't have any entry for it if needed
        initializeIfNeeded(objectId);

        // Should never be null
        return auditLogsCache.get(objectId);
    }

    private void cacheAllAuditLogs() {
        while (allAuditLogsForObjectType.hasNext()) {
            final AuditLog auditLog = allAuditLogsForObjectType.next();
            if ((auditLevel == AuditLevel.MINIMAL && auditLog.getChangeType() == ChangeType.INSERT) || /* Keep only INSERT for MINIMAL */
                auditLevel == AuditLevel.FULL)  /* Keep ALL for FULL */ {
                cacheAuditLog(auditLog);
            }
        }
    }

    private void cacheAuditLog(final AuditLog auditLog) {
        initializeIfNeeded(auditLog.getAuditedEntityId());
        auditLogsCache.get(auditLog.getAuditedEntityId()).add(auditLog);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultAccountAuditLogsForObjectType{");
        sb.append("auditLogsCache=").append(auditLogsCache);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultAccountAuditLogsForObjectType that = (DefaultAccountAuditLogsForObjectType) o;

        if (!auditLogsCache.equals(that.auditLogsCache)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return auditLogsCache.hashCode();
    }
}
