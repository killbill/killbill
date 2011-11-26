package com.ning.billing.account.api;

import java.util.List;

public interface IFieldStore {
    void clear();
    void setValue(String fieldName, String fieldValue);

    String getValue(String fieldName);

    List<ICustomField> getNewFields();

    List<ICustomField> getUpdatedFields();
}
