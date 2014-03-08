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

package org.killbill.billing.jaxrs.resources;

import org.killbill.billing.util.api.AuditLevel;

public class AuditMode {

    private final AuditLevel level;

    public AuditMode(final String auditModeString) {
        this.level = AuditLevel.valueOf(auditModeString.toUpperCase());
    }

    public AuditLevel getLevel() {
        return level;
    }

    public boolean withAudit() {
        return !AuditLevel.NONE.equals(level);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AuditMode");
        sb.append("{level=").append(level);
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

        final AuditMode auditMode = (AuditMode) o;

        if (level != auditMode.level) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return level != null ? level.hashCode() : 0;
    }
}
