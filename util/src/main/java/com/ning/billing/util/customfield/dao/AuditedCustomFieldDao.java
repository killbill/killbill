package com.ning.billing.util.customfield.dao;

import com.ning.billing.util.CallContext;
import com.ning.billing.util.customfield.CustomField;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class AuditedCustomFieldDao {
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
                    // if the tags match, remove from both lists
                    fieldsToUpdate.add(field);
                    fieldIterator.remove();
                    existingFieldIterator.remove();
                }
            }
        }

        customFieldSqlDao.batchInsertFromTransaction(objectId.toString(), objectType, fields, context);
        customFieldSqlDao.batchUpdateFromTransaction(objectId.toString(), objectType, fieldsToUpdate, context);
        customFieldSqlDao.batchDeleteFromTransaction(objectId.toString(), objectType, existingFields, context);

        CustomFieldAuditSqlDao auditDao = dao.become(CustomFieldAuditSqlDao.class);
  //      auditDao.batchInsertFromTransaction(objectId.toString(), objectType, fields, context);
//        auditDao.batchUpdateFromTransaction(objectId.toString(), objectType, fieldsToUpdate, context);
        auditDao.batchDeleteFromTransaction(objectId.toString(), objectType, existingFields, context);
    }
}
