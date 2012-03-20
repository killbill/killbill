package com.ning.billing.util.entity;

import org.joda.time.DateTime;

import java.util.Date;

public abstract class BinderBase {
    protected Date getDate(DateTime dateTime) {
        return dateTime == null ? null : dateTime.toDate();
    }
}
