/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.tenant.dao;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.tenant.api.Tenant;

@BindingAnnotation(TenantBinder.TenantBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface TenantBinder {

    public static class TenantBinderFactory implements BinderFactory {

        @Override
        public Binder<TenantBinder, Tenant> build(final Annotation annotation) {
            return new Binder<TenantBinder, Tenant>() {
                @Override
                public void bind(@SuppressWarnings("rawtypes") final SQLStatement q, final TenantBinder bind, final Tenant tenant) {
                    q.bind("id", tenant.getId().toString());
                    q.bind("externalKey", tenant.getExternalKey());
                    q.bind("apiKey", tenant.getApiKey());
                }
            };
        }
    }
}
