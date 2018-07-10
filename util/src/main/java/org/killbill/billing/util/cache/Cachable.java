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

package org.killbill.billing.util.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.config.tenant.PerTenantConfig;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Cachable {

    String RECORD_ID_CACHE_NAME = "record-id";
    String ACCOUNT_RECORD_ID_CACHE_NAME = "account-record-id";
    String TENANT_RECORD_ID_CACHE_NAME = "tenant-record-id";
    String OBJECT_ID_CACHE_NAME = "object-id";
    String AUDIT_LOG_CACHE_NAME = "audit-log";
    String AUDIT_LOG_VIA_HISTORY_CACHE_NAME = "audit-log-via-history";
    String TENANT_CATALOG_CACHE_NAME = "tenant-catalog";
    String TENANT_PAYMENT_STATE_MACHINE_CONFIG_CACHE_NAME = "tenant-payment-state-machine-config";
    String TENANT_OVERDUE_CONFIG_CACHE_NAME = "tenant-overdue-config";
    String TENANT_CONFIG_CACHE_NAME = "tenant-config";
    String TENANT_KV_CACHE_NAME = "tenant-kv";
    String TENANT_CACHE_NAME = "tenant";
    String OVERRIDDEN_PLAN_CACHE_NAME = "overridden-plan";
    String ACCOUNT_IMMUTABLE_CACHE_NAME = "account-immutable";
    String ACCOUNT_BCD_CACHE_NAME = "account-bcd";
    String ACCOUNT_ID_FROM_BUNDLE_ID_CACHE_NAME = "account-id-from-bundle-id";
    String BUNDLE_ID_FROM_SUBSCRIPTION_ID_CACHE_NAME = "bundle-id-from-subscription-id";

    CacheType value();

    // Make sure both the key and value are Serializable
    enum CacheType {

        /* Mapping from object 'id (UUID as String)' -> object 'recordId (Long)' */
        RECORD_ID(RECORD_ID_CACHE_NAME, String.class, Long.class, false),

        /* Mapping from object 'id (UUID as String)' -> matching account object 'accountRecordId (Long)' */
        ACCOUNT_RECORD_ID(ACCOUNT_RECORD_ID_CACHE_NAME, String.class, Long.class, false),

        /* Mapping from object 'id (UUID as String)' -> matching object 'tenantRecordId (Long)' */
        TENANT_RECORD_ID(TENANT_RECORD_ID_CACHE_NAME, String.class, Long.class, false),

        /* Mapping from object 'recordId (Long as String)' -> object 'id (UUID)'  */
        OBJECT_ID(OBJECT_ID_CACHE_NAME, String.class, UUID.class, true),

        /* Mapping from object 'tableName::targetRecordId' -> matching objects 'List<AuditLogModelDao>' */
        AUDIT_LOG(AUDIT_LOG_CACHE_NAME, String.class, List.class, true),

        /* Mapping from object 'tableName::historyTableName::targetRecordId' -> matching objects 'List<AuditLogModelDao>' */
        AUDIT_LOG_VIA_HISTORY(AUDIT_LOG_VIA_HISTORY_CACHE_NAME, String.class, List.class, true),

        /* Tenant catalog cache */
        TENANT_CATALOG(TENANT_CATALOG_CACHE_NAME, Long.class, Catalog.class, false),

        /* Tenant payment state machine config cache (String -> SerializableStateMachineConfig) */
        TENANT_PAYMENT_STATE_MACHINE_CONFIG(TENANT_PAYMENT_STATE_MACHINE_CONFIG_CACHE_NAME, String.class, Object.class, false),

        /* Tenant overdue config cache (String -> DefaultOverdueConfig) */
        TENANT_OVERDUE_CONFIG(TENANT_OVERDUE_CONFIG_CACHE_NAME, Long.class, Object.class, false),

        /* Tenant overdue config cache */
        TENANT_CONFIG(TENANT_CONFIG_CACHE_NAME, Long.class, PerTenantConfig.class, false),

        /* Tenant config cache */
        TENANT_KV(TENANT_KV_CACHE_NAME, String.class, String.class, false),

        /* Tenant cache */
        TENANT(TENANT_CACHE_NAME, String.class, Tenant.class, false),

        /* Overwritten plans  */
        OVERRIDDEN_PLAN(OVERRIDDEN_PLAN_CACHE_NAME, String.class, Plan.class, false),

        /* Immutable account data config cache */
        ACCOUNT_IMMUTABLE(ACCOUNT_IMMUTABLE_CACHE_NAME, Long.class, ImmutableAccountData.class, false),

        /* Account BCD config cache */
        ACCOUNT_BCD(ACCOUNT_BCD_CACHE_NAME, UUID.class, Integer.class, false),

        /* Bundle id to Account id cache */
        ACCOUNT_ID_FROM_BUNDLE_ID(ACCOUNT_ID_FROM_BUNDLE_ID_CACHE_NAME, UUID.class, UUID.class, false),

        /* Entitlement id to Bundle id cache */
        BUNDLE_ID_FROM_SUBSCRIPTION_ID(BUNDLE_ID_FROM_SUBSCRIPTION_ID_CACHE_NAME, UUID.class, UUID.class, false);

        private final String cacheName;
        private final Class keyType;
        private final Class valueType;
        private final boolean isKeyPrefixedWithTableName;

        CacheType(final String cacheName, final Class keyType, final Class valueType, final boolean isKeyPrefixedWithTableName) {
            this.cacheName = cacheName;
            this.keyType = keyType;
            this.valueType = valueType;
            this.isKeyPrefixedWithTableName = isKeyPrefixedWithTableName;
        }

        public String getCacheName() {
            return cacheName;
        }

        public Class<?> getKeyType() {
            return keyType;
        }

        public Class<?> getValueType() {
            return valueType;
        }

        public boolean isKeyPrefixedWithTableName() { return isKeyPrefixedWithTableName; }

        public static CacheType findByName(final String input) {
            for (final CacheType cacheType : CacheType.values()) {
                if (cacheType.cacheName.equals(input)) {
                    return cacheType;
                }
            }
            return null;
        }
    }
}
