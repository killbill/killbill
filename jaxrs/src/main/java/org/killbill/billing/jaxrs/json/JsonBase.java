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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.audit.AuditLog;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public abstract class JsonBase {

    protected List<AuditLogJson> auditLogs;

    public JsonBase() {
        this(null);
    }

    public JsonBase(@Nullable final List<AuditLogJson> auditLogs) {
        this.auditLogs = auditLogs == null ? ImmutableList.<AuditLogJson>of() : auditLogs;
    }

    protected static ImmutableList<AuditLogJson> toAuditLogJson(@Nullable final List<AuditLog> auditLogs) {
        if (auditLogs == null) {
            return ImmutableList.of();
        }

        return ImmutableList.<AuditLogJson>copyOf(Collections2.transform(auditLogs, new Function<AuditLog, AuditLogJson>() {
            @Override
            public AuditLogJson apply(@Nullable final AuditLog input) {
                return new AuditLogJson(input);
            }
        }));
    }

    protected static String toString(@Nullable final UUID id) {
        return id == null ? null : id.toString();
    }

    public List<AuditLogJson> getAuditLogs() {
        return auditLogs;
    }

    protected List<PluginProperty> propertiesToList(final Map<String, String> propertiesMap) {
        final List<PluginProperty> properties = new LinkedList<PluginProperty>();
        for (final String key : propertiesMap.keySet()) {
            final PluginProperty property = new PluginProperty(key, propertiesMap.get(key), false);
            properties.add(property);
        }
        return properties;
    }

    protected Map<String, Object> propertiesToMap(final Iterable<PluginProperty> properties) {
        final Map<String, Object> propertiesMap = new HashMap<String, Object>();
        for (final PluginProperty pluginProperty : properties) {
            if (pluginProperty.getValue() != null) {
                propertiesMap.put(pluginProperty.getKey(), pluginProperty.getValue());
            }
        }
        return propertiesMap;
    }

    protected static List<PluginPropertyJson> toPluginPropertyJson(final Iterable<PluginProperty> properties) {
        final List<PluginPropertyJson> pluginProperties = new ArrayList<PluginPropertyJson>();
        for (final PluginProperty pluginProperty : properties) {
            pluginProperties.add(new PluginPropertyJson(pluginProperty));
        }
        return pluginProperties;
    }
}
