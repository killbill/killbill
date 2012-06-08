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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.entity.collection.dao.UpdatableEntityCollectionSqlDao;

public abstract class AuditedCollectionDaoBase<T extends Entity> implements AuditedCollectionDao<T> {
    private static final class IdInSetPredicate<T extends Entity> implements Predicate<T> {
        private final Set<UUID> ids;

        public IdInSetPredicate(Set<UUID> ids) {
            this.ids = ids;
        }

        @Override
        public boolean apply(T entity) {
            return ids.contains(entity.getId());
        }
    }

    private final Function<T, UUID> entityIdExtractor = new Function<T, UUID>() {
        @Override
        public UUID apply(T entity) {
            return entity.getId();
        }
    };

    @Override
    public void saveEntitiesFromTransaction(Transmogrifier transactionalDao, UUID objectId, ObjectType objectType, List<T> entities, CallContext context) {
        UpdatableEntityCollectionSqlDao<T> dao = transmogrifyDao(transactionalDao);

        List<T> existingEntities = dao.load(objectId.toString(), objectType);

        // get list of existing entities
        Set<UUID> currentObjIds = new HashSet<UUID>(Collections2.transform(existingEntities, entityIdExtractor));
        Set<UUID> updatedObjIds = new HashSet<UUID>(Collections2.transform(entities, entityIdExtractor));

        Set<UUID> idsOfObjsToRemove = Sets.difference(currentObjIds, updatedObjIds);
        Set<UUID> idsOfObjsToAdd = Sets.difference(updatedObjIds, currentObjIds);
        Set<UUID> idsOfObjsToUpdate = Sets.intersection(currentObjIds, updatedObjIds);

        Collection<T> objsToRemove = Collections2.filter(existingEntities, new IdInSetPredicate<T>(idsOfObjsToRemove));
        Collection<T> objsToAdd = Collections2.filter(entities, new IdInSetPredicate<T>(idsOfObjsToAdd));
        Collection<T> objsToUpdate = Collections2.filter(existingEntities, new IdInSetPredicate<T>(idsOfObjsToUpdate));

        if (objsToAdd.size() != 0) {
            dao.insertFromTransaction(objectId.toString(), objectType, objsToAdd, context);
        }

        if (objsToUpdate.size() != 0) {
            dao.updateFromTransaction(objectId.toString(), objectType, objsToUpdate, context);
        }

        // get all custom entities (including those that are about to be deleted) from the database in order to get the record ids
        List<Mapper<UUID, Long>> recordIds = dao.getRecordIds(objectId.toString(), objectType);
        Map<UUID, Long> recordIdMap = convertToHistoryMap(recordIds);

        if (objsToRemove.size() != 0) {
            dao.deleteFromTransaction(objectId.toString(), objectType, objsToRemove, context);
        }

        List<EntityHistory<T>> entityHistories = new ArrayList<EntityHistory<T>>();
        entityHistories.addAll(convertToHistory(objsToAdd, recordIdMap, ChangeType.INSERT));
        entityHistories.addAll(convertToHistory(objsToUpdate, recordIdMap, ChangeType.UPDATE));
        entityHistories.addAll(convertToHistory(objsToRemove, recordIdMap, ChangeType.DELETE));

        Long maxHistoryRecordId = dao.getMaxHistoryRecordId();
        dao.addHistoryFromTransaction(objectId.toString(), objectType, entityHistories, context);

        // have to fetch history record ids to update audit log
        List<Mapper<Long, Long>> historyRecordIds = dao.getHistoryRecordIds(maxHistoryRecordId);
        Map<Long, Long> historyRecordIdMap = convertToAuditMap(historyRecordIds);
        List<EntityAudit> entityAudits = convertToAudits(entityHistories, historyRecordIdMap);

        dao.insertAuditFromTransaction(entityAudits, context);
    }

    @Override
    public void saveEntities(UUID objectId, ObjectType objectType, List<T> entities, CallContext context) {
        this.saveEntitiesFromTransaction(getSqlDao(), objectId, objectType, entities, context);
    }

    @Override
    public Map<String, T> loadEntities(final UUID objectId, final ObjectType objectType) {
        UpdatableEntityCollectionSqlDao<T> thisDao = getSqlDao();
        return getMap(thisDao, objectId, objectType);
    }

    @Override
    public Map<String, T> loadEntitiesFromTransaction(final Transmogrifier dao, final UUID objectId, final ObjectType objectType) {
        UpdatableEntityCollectionSqlDao<T> thisDao = transmogrifyDao(dao);
        return getMap(thisDao, objectId, objectType);
    }

    private Map<String, T> getMap(final UpdatableEntityCollectionSqlDao<T> dao, final UUID objectId, final ObjectType objectType) {
        List<T> entities = dao.load(objectId.toString(), objectType);
        Map<String, T> results = new HashMap<String, T>();
        for (T entity : entities) {
            results.put(getKey(entity), entity);
        }
        return results;
    }

    protected List<EntityHistory<T>> convertToHistory(Collection<T> entities, Map<UUID, Long> recordIds, ChangeType changeType) {
        List<EntityHistory<T>> histories = new ArrayList<EntityHistory<T>>();

        for (T entity : entities) {
            UUID id = entity.getId();
            histories.add(new EntityHistory<T>(id, recordIds.get(id), entity, changeType));
        }

        return histories;
    }

    protected List<EntityAudit> convertToAudits(List<EntityHistory<T>> histories, Map<Long, Long> historyRecordIds) {
        List<EntityAudit> audits = new ArrayList<EntityAudit>();

        for (EntityHistory<T> history : histories) {
            Long recordId = history.getValue();
            Long historyRecordId = historyRecordIds.get(recordId);
            audits.add(new EntityAudit(getTableName(), historyRecordId, history.getChangeType()));
        }

        return audits;
    }

    protected Map<UUID, Long> convertToHistoryMap(List<Mapper<UUID, Long>> recordIds) {
        Map<UUID, Long> recordIdMap = new HashMap<UUID, Long>();
        for (Mapper<UUID, Long> recordId : recordIds) {
            recordIdMap.put(recordId.getKey(), recordId.getValue());
        }
        return recordIdMap;
    }

    protected Map<Long, Long> convertToAuditMap(List<Mapper<Long, Long>> historyRecordIds) {
        Map<Long, Long> historyRecordIdMap = new HashMap<Long, Long>();
        for (Mapper<Long, Long> historyRecordId : historyRecordIds) {
            historyRecordIdMap.put(historyRecordId.getKey(), historyRecordId.getValue());
        }
        return historyRecordIdMap;
    }

    protected abstract TableName getTableName();
    protected abstract UpdatableEntityCollectionSqlDao<T> transmogrifyDao(Transmogrifier transactionalDao);
    protected abstract UpdatableEntityCollectionSqlDao<T> getSqlDao();
    protected abstract String getKey(T entity);
}
