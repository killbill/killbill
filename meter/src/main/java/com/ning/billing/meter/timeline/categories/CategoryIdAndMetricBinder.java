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

package com.ning.billing.meter.timeline.categories;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.meter.timeline.categories.CategoryIdAndMetricBinder.CategoryIdAndMetricBinderFactory;

@BindingAnnotation(CategoryIdAndMetricBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface CategoryIdAndMetricBinder {

    public static class CategoryIdAndMetricBinderFactory implements BinderFactory {

        public Binder build(final Annotation annotation) {
            return new Binder<CategoryIdAndMetricBinder, CategoryRecordIdAndMetric>() {
                public void bind(final SQLStatement query, final CategoryIdAndMetricBinder binder, final CategoryRecordIdAndMetric categoryRecordAndKind) {
                    query.bind("eventCategoryId", categoryRecordAndKind.getEventCategoryId())
                         .bind("metric", categoryRecordAndKind.getMetric());
                }
            };
        }
    }
}
