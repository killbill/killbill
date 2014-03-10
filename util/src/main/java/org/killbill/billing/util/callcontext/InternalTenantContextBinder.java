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

package org.killbill.billing.util.callcontext;

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

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.callcontext.InternalTenantContextBinder.InternalTenantContextBinderFactory;

@BindingAnnotation(InternalTenantContextBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface InternalTenantContextBinder {

    public static class InternalTenantContextBinderFactory implements BinderFactory {

        @Override
        public Binder build(final Annotation annotation) {
            return new Binder<InternalTenantContextBinder, InternalTenantContext>() {
                @Override
                public void bind(final SQLStatement q, final InternalTenantContextBinder bind, final InternalTenantContext context) {
                    if (context.getTenantRecordId() == null) {
                        // TODO - shouldn't be null, but for now...
                        q.bindNull("tenantRecordId", Types.INTEGER);
                    } else {
                        q.bind("tenantRecordId", context.getTenantRecordId());
                    }

                    if (context.getAccountRecordId() == null) {
                        q.bindNull("accountRecordId", Types.INTEGER);
                    } else {
                        q.bind("accountRecordId", context.getAccountRecordId());
                    }

                    if (context instanceof InternalCallContext) {
                        final InternalCallContext callContext = (InternalCallContext) context;
                        q.bind("userName", callContext.getCreatedBy());
                        if (callContext.getCreatedDate() == null) {
                            q.bindNull("createdDate", Types.DATE);
                        } else {
                            q.bind("createdDate", callContext.getCreatedDate().toDate());
                        }
                        if (callContext.getUpdatedDate() == null) {
                            q.bindNull("updatedDate", Types.DATE);
                        } else {
                            q.bind("updatedDate", callContext.getUpdatedDate().toDate());
                        }
                        q.bind("reasonCode", callContext.getReasonCode());
                        q.bind("comments", callContext.getComments());
                        q.bind("userToken", (callContext.getUserToken() != null) ? callContext.getUserToken().toString() : null);
                    }
                }
            };
        }
    }
}
