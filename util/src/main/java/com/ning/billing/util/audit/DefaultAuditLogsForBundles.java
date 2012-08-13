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

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultAuditLogsForBundles implements AuditLogsForBundles {

    private final Map<UUID, List<AuditLog>> bundlesAuditLogs;

    public DefaultAuditLogsForBundles(final Map<UUID, List<AuditLog>> bundlesAuditLogs) {
        this.bundlesAuditLogs = bundlesAuditLogs;
    }

    @Override
    public Map<UUID, List<AuditLog>> getBundlesAuditLogs() {
        return bundlesAuditLogs;
    }

    @Override
    public Map<UUID, List<AuditLog>> getSubscriptionEventsAuditLogs() {
        // TODO
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultAuditLogsForBundles");
        sb.append("{bundlesAuditLogs=").append(bundlesAuditLogs);
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

        final DefaultAuditLogsForBundles that = (DefaultAuditLogsForBundles) o;

        if (bundlesAuditLogs != null ? !bundlesAuditLogs.equals(that.bundlesAuditLogs) : that.bundlesAuditLogs != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return bundlesAuditLogs != null ? bundlesAuditLogs.hashCode() : 0;
    }
}
