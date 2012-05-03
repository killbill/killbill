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

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.CustomFieldHistory;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class AuditedCustomFieldDao implements CustomFieldDao {
    @Override
    public void saveFields(Transmogrifier dao, UUID objectId, String objectType, List<CustomField> fields, CallContext context) {
        CustomFieldSqlDao customFieldSqlDao = dao.become(CustomFieldSqlDao.class);

        // get list of existing fields
        List<CustomField> existingFields = customFieldSqlDao.load(objectId.toString(), objectType);
        List<CustomField> fieldsToUpdate = new ArrayList<CustomField>();

        // sort into fields to update (fieldsToUpdate), fields to add (fields), and fields to delete (existingFields)
        Iterator<CustomField> fieldIterator = fields.iterator();
        while (fieldIterator.hasNext()) {
            CustomField field = fieldIterator.next();

            Iterator<CustomField> existingFieldIterator = existingFields.iterator();
            while (existingFieldIterator.hasNext()) {
                CustomField existingField = existingFieldIterator.next();
                if (field.getName().equals(existingField.getName())) {
                    // if the tagStore match, remove from both lists
                    fieldsToUpdate.add(field);
                    fieldIterator.remove();
                    existingFieldIterator.remove();
                }
            }
        }

        customFieldSqlDao.batchInsertFromTransaction(objectId.toString(), objectType, fields, context);
        customFieldSqlDao.batchUpdateFromTransaction(objectId.toString(), objectType, fieldsToUpdate, context);
        customFieldSqlDao.batchDeleteFromTransaction(objectId.toString(), objectType, existingFields, context);

        List<CustomFieldHistory> fieldHistories = new ArrayList<CustomFieldHistory>();
        fieldHistories.addAll(convertToHistoryEntry(fields, ChangeType.INSERT));
        fieldHistories.addAll(convertToHistoryEntry(fieldsToUpdate, ChangeType.UPDATE));
        fieldHistories.addAll(convertToHistoryEntry(existingFields, ChangeType.DELETE));

        CustomFieldHistorySqlDao historyDao = dao.become(CustomFieldHistorySqlDao.class);
        historyDao.batchAddHistoryFromTransaction(objectId.toString(), objectType, fieldHistories, context);

        CustomFieldAuditSqlDao auditDao = dao.become(CustomFieldAuditSqlDao.class);
        auditDao.batchInsertAuditLogFromTransaction(objectId.toString(), objectType, fieldHistories, context);
    }

    private List<CustomFieldHistory> convertToHistoryEntry(List<CustomField> fields, ChangeType changeType) {
        List<CustomFieldHistory> result = new ArrayList<CustomFieldHistory>();

        for (CustomField field : fields) {
            result.add(new CustomFieldHistory(field, changeType));
        }

        return result;
    }
}
