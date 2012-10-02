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

package com.ning.billing.util.customfield.dao;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.dao.AuditedCollectionDaoBase;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.collection.dao.UpdatableEntityCollectionSqlDao;

import com.google.inject.Inject;

public class AuditedCustomFieldDao extends AuditedCollectionDaoBase<CustomField, String> implements CustomFieldDao {

    private final CustomFieldSqlDao dao;

    @Inject
    public AuditedCustomFieldDao(final IDBI dbi) {
        dao = dbi.onDemand(CustomFieldSqlDao.class);
    }

    @Override
    protected TableName getTableName(final InternalTenantContext context) {
        return TableName.CUSTOM_FIELD_HISTORY;
    }

    @Override
    protected String getEquivalenceObjectFor(final CustomField obj) {
        return obj.getName();
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<CustomField> transmogrifyDao(final Transmogrifier transactionalDao, InternalTenantContext context) {
        return transactionalDao.become(CustomFieldSqlDao.class);
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<CustomField> getSqlDao(final InternalTenantContext context) {
        return dao;
    }

    @Override
    protected String getKey(final CustomField entity, final InternalTenantContext context) {
        return entity.getName();
    }

    //    @Override
    //    public void saveEntitiesFromTransaction(Transmogrifier dao, UUID objectId, ObjectType objectType, List<CustomField> fields, CallContext context) {
    //        CustomFieldSqlDao customFieldSqlDao = dao.become(CustomFieldSqlDao.class);
    //
    //        // get list of existing fields
    //        List<CustomField> existingFields = customFieldSqlDao.load(objectId.toString(), objectType);
    //        List<CustomField> fieldsToUpdate = new ArrayList<CustomField>();
    //
    //        // sort into fields to update (fieldsToUpdate), fields to add (fields), and fields to delete (existingFields)
    //        Iterator<CustomField> fieldIterator = fields.iterator();
    //        while (fieldIterator.hasNext()) {
    //            CustomField field = fieldIterator.next();
    //
    //            Iterator<CustomField> existingFieldIterator = existingFields.iterator();
    //            while (existingFieldIterator.hasNext()) {
    //                CustomField existingField = existingFieldIterator.next();
    //                if (field.getName().equals(existingField.getName())) {
    //                    // if the tagStore match, remove from both lists
    //                    fieldsToUpdate.add(field);
    //                    fieldIterator.remove();
    //                    existingFieldIterator.remove();
    //                }
    //            }
    //        }
    //
    //        customFieldSqlDao.batchInsertFromTransaction(objectId.toString(), objectType, fields, context);
    //        customFieldSqlDao.batchUpdateFromTransaction(objectId.toString(), objectType, fieldsToUpdate, context);
    //
    //        // get all custom fields (including those that are about to be deleted) from the database in order to get the record ids
    //        List<Mapper> recordIds = customFieldSqlDao.getRecordIds(objectId.toString(), objectType);
    //        Map<UUID, Long> recordIdMap = new HashMap<UUID, Long>();
    //        for (Mapper recordId : recordIds) {
    //            recordIdMap.put(recordId.getId(), recordId.getRecordId());
    //        }
    //
    //        customFieldSqlDao.batchDeleteFromTransaction(objectId.toString(), objectType, existingFields, context);
    //
    //        List<MappedEntity<CustomField>> fieldHistories = new ArrayList<MappedEntity<CustomField>>();
    //        fieldHistories.addAll(convertToHistory(fields, recordIdMap, ChangeType.INSERT));
    //        fieldHistories.addAll(convertToHistory(fieldsToUpdate, recordIdMap, ChangeType.UPDATE));
    //        fieldHistories.addAll(convertToHistory(existingFields, recordIdMap, ChangeType.DELETE));
    //
    //        customFieldSqlDao.batchAddHistoryFromTransaction(objectId.toString(), objectType, fieldHistories, context);
    //        customFieldSqlDao.batchInsertAuditLogFromTransaction(TableName.CUSTOM_FIELD_HISTORY, objectId.toString(), objectType, fieldHistories, context);
    //    }
    //
    //    private List<MappedEntity<CustomField>> convertToHistory(List<CustomField> fields, Map<UUID, Long> recordIds, ChangeType changeType) {
    //        List<MappedEntity<CustomField>> result = new ArrayList<MappedEntity<CustomField>>();
    //
    //        for (CustomField field : fields) {
    //            result.add(new MappedEntity<CustomField>(recordIds.get(field.getId()), field, changeType));
    //        }
    //
    //        return result;
    //    }
}
