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
import java.util.UUID;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Cachable {

    public final String RECORD_ID_CACHE_NAME = "record-id";
    public final String ACCOUNT_RECORD_ID_CACHE_NAME = "account-record-id";
    public final String TENANT_RECORD_ID_CACHE_NAME = "tenant-record-id";

    public CacheType value();

    public enum CacheType {
        /* Mapping from object 'id (UUID)' -> object 'recordId (Long' */
        RECORD_ID(RECORD_ID_CACHE_NAME, UUID.class, Long.class),

        /* Mapping from object 'id (UUID)' -> matching account object 'accountRecordId (Long)' */
        ACCOUNT_RECORD_ID(ACCOUNT_RECORD_ID_CACHE_NAME, UUID.class, Long.class),


        /* Mapping from object 'id (UUID)' -> matching object 'tenantRecordId (Long)' */
        TENANT_RECORD_ID(TENANT_RECORD_ID_CACHE_NAME, UUID.class, Long.class);

        private final String cacheName;
        private final Class key;
        private final Class value;

        CacheType(final String cacheName, final Class key, final Class value) {
            this.cacheName = cacheName;
            this.key = key;
            this.value = value;
        }

        public Class getKey() {
            return key;
        }

        public Class getValue() {
            return value;
        }

        public static CacheType findByName(final String input) {
            for (CacheType cur : CacheType.values()) {
                if (cur.cacheName.equals(input)) {
                    return cur;
                }
            }
            return null;
        }
    }
}
