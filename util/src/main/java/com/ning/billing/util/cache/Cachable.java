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

package com.ning.billing.util.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Cachable {

    public final String RECORD_ID_CACHE_NAME = "record-id";
    public final String ACCOUNT_RECORD_ID_CACHE_NAME = "account-record-id";
    public final String TENANT_RECORD_ID_CACHE_NAME = "tenant-record-id";
    public final String AUDIT_LOG_CACHE_NAME = "audit-log";
    public final String AUDIT_LOG_VIA_HISTORY_CACHE_NAME = "audit-log-via-history";

    public CacheType value();

    public enum CacheType {
        /* Mapping from object 'id (UUID)' -> object 'recordId (Long' */
        RECORD_ID(RECORD_ID_CACHE_NAME),

        /* Mapping from object 'id (UUID)' -> matching account object 'accountRecordId (Long)' */
        ACCOUNT_RECORD_ID(ACCOUNT_RECORD_ID_CACHE_NAME),

        /* Mapping from object 'id (UUID)' -> matching object 'tenantRecordId (Long)' */
        TENANT_RECORD_ID(TENANT_RECORD_ID_CACHE_NAME),

        /* Mapping from object 'tableName::targetRecordId' -> matching objects 'Iterable<AuditLog>' */
        AUDIT_LOG(AUDIT_LOG_CACHE_NAME),

        /* Mapping from object 'tableName::historyTableName::targetRecordId' -> matching objects 'Iterable<AuditLog>' */
        AUDIT_LOG_VIA_HISTORY(AUDIT_LOG_VIA_HISTORY_CACHE_NAME);

        private final String cacheName;

        CacheType(final String cacheName) {
            this.cacheName = cacheName;
        }

        public String getCacheName() {
            return cacheName;
        }

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
