package org.killbill.billing.catalog.dao;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;

import org.killbill.billing.catalog.dao.UsageTierKeysCollectionBinder.UsageTierKeysCollectionBinderFactory;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

@BindingAnnotation(UsageTierKeysCollectionBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface UsageTierKeysCollectionBinder {

    public static class UsageTierKeysCollectionBinderFactory implements BinderFactory {

        @Override
        public Binder build(Annotation annotation) {
            return new Binder<UsageTierKeysCollectionBinder, Collection<String>>() {

                @Override
                public void bind(SQLStatement<?> query, UsageTierKeysCollectionBinder bind, Collection<String> keys) {
                    query.define("keys", keys);

                    int idx = 0;
                    for (String state : keys) {
                        query.bind("key_" + idx, state);
                        idx++;
                    }
                }
            };
        }
    }
}
