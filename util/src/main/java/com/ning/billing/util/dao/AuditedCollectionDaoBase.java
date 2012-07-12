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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.google.common.collect.Sets;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.entity.collection.dao.UpdatableEntityCollectionSqlDao;

public abstract class AuditedCollectionDaoBase<T extends Entity, V> implements AuditedCollectionDao<T> {
    /**
     * Returns equivalence object for the entities, so that dao
     * can figure out if entities have changed (UPDATE statement) or
     * are new (INSERT statement).
     * If two entities return the equivalence objects that are equal themselves
     * (and have the same hashCode), then the entities are equivalent.
     * For example, two custom field instances are equivalent (describe the same
     * custom field) if their name is the same (the equivalence object is the
     * name string). The instances can still have different custom field values
     * (represent two different 'assignments' to that
     * field), which should result in UPDATE statements in the dao.
     */
    protected abstract V getEquivalenceObjectFor(T obj);

    /**
     * Update all entities of given type objectType for the given objectId, e.g. if T is AccountEmail, objectId will
     * represent an account id and objectType will be ACCOUNT_EMAIL.
     * <p/>
     * This will add and delete entities as needed.
     *
     * @param transactionalDao the current dao (in the transaction)
     * @param objectId         the parent object id
     * @param objectType       the entity object type
     * @param newEntities      the final list of entities
     * @param context          the current content
     */
    @Override
    public void saveEntitiesFromTransaction(final Transmogrifier transactionalDao, final UUID objectId, final ObjectType objectType, final List<T> newEntities, final CallContext context) {
        final UpdatableEntityCollectionSqlDao<T> dao = transmogrifyDao(transactionalDao);

        // Get list of all existing entities for this parent object, e.g. find all email addresses for this account
        final List<T> currentEntities = dao.load(objectId.toString(), objectType);

        // Compute the list of objects to add, remove and/or update
        final Map<V, T> currentObjs = new HashMap<V, T>(currentEntities.size());
        final Map<V, T> updatedObjs = new HashMap<V, T>(newEntities.size());

        for (final T currentObj : currentEntities) {
            currentObjs.put(getEquivalenceObjectFor(currentObj), currentObj);
        }
        for (final T updatedObj : newEntities) {
            updatedObjs.put(getEquivalenceObjectFor(updatedObj), updatedObj);
        }

        final Set<V> equivToRemove = Sets.difference(currentObjs.keySet(), updatedObjs.keySet());
        final Set<V> equivToAdd = Sets.difference(updatedObjs.keySet(), currentObjs.keySet());
        final Set<V> equivToCheckForUpdate = Sets.intersection(updatedObjs.keySet(), currentObjs.keySet());

        final List<T> objsToAdd = new ArrayList<T>(equivToAdd.size());
        final List<T> objsToRemove = new ArrayList<T>(equivToRemove.size());
        final List<T> objsToUpdate = new ArrayList<T>(equivToCheckForUpdate.size());

        for (final V equiv : equivToAdd) {
            objsToAdd.add(updatedObjs.get(equiv));
        }
        for (final V equiv : equivToRemove) {
            objsToRemove.add(currentObjs.get(equiv));
        }
        for (final V equiv : equivToCheckForUpdate) {
            final T currentObj = currentObjs.get(equiv);
            final T updatedObj = updatedObjs.get(equiv);
            if (!currentObj.equals(updatedObj)) {
                objsToUpdate.add(updatedObj);
            }
        }

        // Perform the inserts
        if (objsToAdd.size() != 0) {
            dao.insertFromTransaction(objectId.toString(), objectType, objsToAdd, context);
        }

        // Perform the updates
        if (objsToUpdate.size() != 0) {
            dao.updateFromTransaction(objectId.toString(), objectType, objsToUpdate, context);
        }

        // Find all pairs <entity id, record id> (including those that are about to be deleted) for this parent object
        final List<Mapper<UUID, Long>> recordIds = dao.getRecordIds(objectId.toString(), objectType);
        // Flip the map to look up the record id associated with an entity id
        final Map<UUID, Long> recordIdMap = convertToHistoryMap(recordIds, objectType);

        // Perform the deletes
        if (objsToRemove.size() != 0) {
            dao.deleteFromTransaction(objectId.toString(), objectType, objsToRemove, context);
        }

        // Create the history objects
        final List<EntityHistory<T>> entityHistories = new ArrayList<EntityHistory<T>>();
        entityHistories.addAll(convertToHistory(objsToAdd, recordIdMap, ChangeType.INSERT));
        entityHistories.addAll(convertToHistory(objsToUpdate, recordIdMap, ChangeType.UPDATE));
        entityHistories.addAll(convertToHistory(objsToRemove, recordIdMap, ChangeType.DELETE));

        final Long maxHistoryRecordId = dao.getMaxHistoryRecordId();
        // Save the records in the history table
        dao.addHistoryFromTransaction(objectId.toString(), objectType, entityHistories, context);

        // We have to fetch history record ids to update audit log
        final List<Mapper<Long, Long>> historyRecordIds = dao.getHistoryRecordIds(maxHistoryRecordId);
        final Map<Long, Long> historyRecordIdMap = convertToAuditMap(historyRecordIds);
        final List<EntityAudit> entityAudits = convertToAudits(entityHistories, historyRecordIdMap);

        // Save an entry in the audit log
        dao.insertAuditFromTransaction(entityAudits, context);
    }

    @Override
    public void saveEntities(final UUID objectId, final ObjectType objectType, final List<T> entities, final CallContext context) {
        this.saveEntitiesFromTransaction(getSqlDao(), objectId, objectType, entities, context);
    }

    @Override
    public Map<String, T> loadEntities(final UUID objectId, final ObjectType objectType) {
        final UpdatableEntityCollectionSqlDao<T> thisDao = getSqlDao();
        return getMap(thisDao, objectId, objectType);
    }

    @Override
    public Map<String, T> loadEntitiesFromTransaction(final Transmogrifier dao, final UUID objectId, final ObjectType objectType) {
        final UpdatableEntityCollectionSqlDao<T> thisDao = transmogrifyDao(dao);
        return getMap(thisDao, objectId, objectType);
    }

    private Map<String, T> getMap(final UpdatableEntityCollectionSqlDao<T> dao, final UUID objectId, final ObjectType objectType) {
        final List<T> entities = dao.load(objectId.toString(), objectType);
        final Map<String, T> results = new HashMap<String, T>();
        for (final T entity : entities) {
            results.put(getKey(entity), entity);
        }
        return results;
    }

    protected List<EntityHistory<T>> convertToHistory(final Collection<T> entities, final Map<UUID, Long> recordIds, final ChangeType changeType) {
        final List<EntityHistory<T>> histories = new ArrayList<EntityHistory<T>>();

        for (final T entity : entities) {
            final UUID id = entity.getId();

            final Long recordId = recordIds.get(id);
            if (recordId == null) {
                throw new IllegalStateException("recordId for object " + entity.getClass() + " is null ");
            }
            histories.add(new EntityHistory<T>(id, recordId, entity, changeType));
        }

        return histories;
    }

    protected List<EntityAudit> convertToAudits(final List<EntityHistory<T>> histories, final Map<Long, Long> historyRecordIds) {
        final List<EntityAudit> audits = new ArrayList<EntityAudit>();

        for (final EntityHistory<T> history : histories) {
            final Long recordId = history.getValue();
            final Long historyRecordId = historyRecordIds.get(recordId);
            audits.add(new EntityAudit(getTableName(), historyRecordId, history.getChangeType()));
        }

        return audits;
    }

    protected Map<UUID, Long> convertToHistoryMap(final List<Mapper<UUID, Long>> recordIds, final ObjectType objectType) {
        final Map<UUID, Long> recordIdMap = new HashMap<UUID, Long>();
        for (final Mapper<UUID, Long> recordId : recordIds) {
            if (recordId.getKey() == null) {
                throw new IllegalStateException("UUID for object " + objectType + " is null for recordId " + recordId.getValue());
            }
            if (recordId.getValue() == null) {
                throw new IllegalStateException("recordId for object " + objectType + " is null for UUID " + recordId.getKey());
            }

            recordIdMap.put(recordId.getKey(), recordId.getValue());
        }

        return recordIdMap;
    }

    protected Map<Long, Long> convertToAuditMap(final List<Mapper<Long, Long>> historyRecordIds) {
        final Map<Long, Long> historyRecordIdMap = new HashMap<Long, Long>();
        for (final Mapper<Long, Long> historyRecordId : historyRecordIds) {
            historyRecordIdMap.put(historyRecordId.getKey(), historyRecordId.getValue());
        }
        return historyRecordIdMap;
    }

    protected abstract TableName getTableName();

    protected abstract UpdatableEntityCollectionSqlDao<T> transmogrifyDao(Transmogrifier transactionalDao);

    protected abstract UpdatableEntityCollectionSqlDao<T> getSqlDao();

    protected abstract String getKey(T entity);
}
