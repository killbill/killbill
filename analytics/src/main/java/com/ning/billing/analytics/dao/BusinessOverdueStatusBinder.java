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

package com.ning.billing.analytics.dao;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.Types;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.analytics.model.BusinessOverdueStatus;

@BindingAnnotation(BusinessOverdueStatusBinder.BosBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface BusinessOverdueStatusBinder {
    public static class BosBinderFactory implements BinderFactory {
        public Binder build(final Annotation annotation) {
            return new Binder<BusinessOverdueStatusBinder, BusinessOverdueStatus>() {
                public void bind(final SQLStatement q, final BusinessOverdueStatusBinder bind, final BusinessOverdueStatus overdueStatus) {
                    q.bind("account_key", overdueStatus.getAccountKey());
                    q.bind("bundle_id", overdueStatus.getBundleId().toString());
                    q.bind("external_key", overdueStatus.getExternalKey());
                    q.bind("status", overdueStatus.getStatus());

                    if (overdueStatus.getStartDate() != null) {
                        q.bind("start_date", overdueStatus.getStartDate().getMillis());
                    } else {
                        q.bindNull("start_date", Types.BIGINT);
                    }

                    if (overdueStatus.getEndDate() != null) {
                        q.bind("end_date", overdueStatus.getEndDate().getMillis());
                    } else {
                        q.bindNull("end_date", Types.BIGINT);
                    }
                }
            };
        }
    }
}
