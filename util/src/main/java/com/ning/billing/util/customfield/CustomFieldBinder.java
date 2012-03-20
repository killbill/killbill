package com.ning.billing.util.customfield;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@BindingAnnotation(CustomFieldBinder.CustomFieldBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface CustomFieldBinder {
    public static class CustomFieldBinderFactory implements BinderFactory {
        @Override
        public Binder build(Annotation annotation) {
            return new Binder<CustomFieldBinder, CustomField>() {
                @Override
                public void bind(SQLStatement q, CustomFieldBinder bind, CustomField customField) {
                    q.bind("id", customField.getId().toString());
                    q.bind("fieldName", customField.getName());
                    q.bind("fieldValue", customField.getValue());
                }
            };
        }
    }
}
