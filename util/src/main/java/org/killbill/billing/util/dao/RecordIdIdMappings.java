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

package org.killbill.billing.util.dao;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RecordIdIdMappings {

    private final Long recordId;
    private final UUID id;

    public RecordIdIdMappings(final long recordId, final UUID id) {
        this.recordId = recordId;
        this.id = id;
    }

    public Long getRecordId() {
        return recordId;
    }

    public UUID getId() {
        return id;
    }

    public static Map<Long, UUID> toMap(final Iterable<RecordIdIdMappings> mappings) {
        final Map<Long, UUID> result = new LinkedHashMap<Long, UUID>();
        for (final RecordIdIdMappings mapping : mappings) {
            result.put(mapping.getRecordId(), mapping.getId());
        }
        return result;
    }
}
