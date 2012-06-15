/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.util.dao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.entity.Entity;

public interface AuditedCollectionDao<T extends Entity> {
    void saveEntitiesFromTransaction(Transmogrifier transactionalDao, UUID objectId, ObjectType objectType,
                                     List<T> entities, CallContext context);

    void saveEntities(UUID objectId, ObjectType objectType, List<T> entities, CallContext context);

    Map<String, T> loadEntities(UUID objectId, ObjectType objectType);

    Map<String, T> loadEntitiesFromTransaction(Transmogrifier dao, UUID objectId, ObjectType objectType);
}
