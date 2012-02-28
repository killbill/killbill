package com.ning.billing.util.validation;

import java.util.HashMap;
import java.util.Map;

public class ValidationConfiguration extends HashMap<String, ColumnInfo> {
    public void addMapping(String propertyName, ColumnInfo columnInfo) {
        super.put(propertyName, columnInfo);
    }

    public boolean hasMapping(String propertyName) {
        return super.get(propertyName) != null;
    }
}
