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

package com.ning.billing.util.tag.dao;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.tag.Tag;

@BindingAnnotation(TagHistoryBinder.TagHistoryBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface TagHistoryBinder {
    public static class TagHistoryBinderFactory implements BinderFactory {
        @Override
        public Binder build(final Annotation annotation) {
            return new Binder<TagHistoryBinder, EntityHistory<Tag>>() {
                @Override
                public void bind(final SQLStatement<?> q, final TagHistoryBinder bind, final EntityHistory<Tag> tagHistory) {

                    //q.bind("recordId", tagHistory.getValue());
                    q.bind("changeType", tagHistory.getChangeType().toString());
                    q.bind("id", tagHistory.getId().toString());
                    q.bind("tagDefinitionId", tagHistory.getEntity().getTagDefinitionId().toString());
                }
            };
        }
    }
}
