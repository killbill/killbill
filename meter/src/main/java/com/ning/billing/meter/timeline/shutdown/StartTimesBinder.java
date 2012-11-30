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

package com.ning.billing.meter.timeline.shutdown;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.shutdown.StartTimesBinder.StartTimesBinderFactory;
import com.ning.billing.meter.timeline.util.DateTimeUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

@BindingAnnotation(StartTimesBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface StartTimesBinder {

    public static class StartTimesBinderFactory implements BinderFactory {

        private static final Logger log = LoggerFactory.getLogger(StartTimesBinderFactory.class);
        private static final ObjectMapper mapper = new ObjectMapper();

        public Binder build(final Annotation annotation) {
            return new Binder<StartTimesBinder, StartTimes>() {
                public void bind(final SQLStatement query, final StartTimesBinder binder, final StartTimes startTimes) {
                    try {
                        final String s = mapper.writeValueAsString(startTimes.getStartTimesMap());
                        query.bind("startTimes", s)
                             .bind("timeInserted", DateTimeUtils.unixSeconds(startTimes.getTimeInserted()));
                    } catch (IOException e) {
                        log.error("Exception while binding StartTimes", e);
                    }
                }
            };
        }
    }
}
