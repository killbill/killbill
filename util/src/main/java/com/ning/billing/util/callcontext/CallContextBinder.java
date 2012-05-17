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

package com.ning.billing.util.callcontext;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@BindingAnnotation(CallContextBinder.CallContextBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface CallContextBinder {
    public static class CallContextBinderFactory implements BinderFactory {
        @Override
        public Binder build(Annotation annotation) {
            return new Binder<CallContextBinder, CallContext>() {
                @Override
                public void bind(SQLStatement q, CallContextBinder bind, CallContext callContext) {
                	q.bind("userName", callContext.getUserName());
                    q.bind("createdDate", callContext.getCreatedDate().toDate());
                    q.bind("updatedDate", callContext.getUpdatedDate().toDate());
                    q.bind("reasonCode", callContext.getReasonCode());
                    q.bind("comment", callContext.getComment());
                	q.bind("userToken", (callContext.getUserToken() != null) ? callContext.getUserToken().toString() : null);                	
                }
            };
        }
    }
}