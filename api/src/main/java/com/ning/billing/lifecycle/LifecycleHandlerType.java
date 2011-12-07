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

package com.ning.billing.lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import com.google.common.collect.Lists;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LifecycleHandlerType {


    //
    // The level themselves are still work in progress depending on what we really need
    //
    // Ordering is important in that enum
    //
    public enum LifecycleLevel {

        /**
         * Load and validate catalog (only for catalog subsytem)
         */
        LOAD_CATALOG(Sequence.STARTUP_PRE_EVENT_REGISTRATION),
        /**
         * Initialize event bus (only for the event bus)
         */
        INIT_BUS(Sequence.STARTUP_PRE_EVENT_REGISTRATION),
        /**
         * Service specific initalization-- service does not start yet
         */
        INIT_SERVICE(Sequence.STARTUP_PRE_EVENT_REGISTRATION),
        /**
         * Service register their interest in events
         */
        REGISTER_EVENTS(Sequence.STARTUP_PRE_EVENT_REGISTRATION),
        /**
         * Service start
         * - API call should not work
         * - Events might be triggered
         * - Batch processing jobs started
         */
        START_SERVICE(Sequence.STARTUP_POST_EVENT_REGISTRATION),
        /**
         * Stop service
         */
        STOP_SERVICE(Sequence.SHUTDOWN_PRE_EVENT_UNREGISTRATION),
        /**
         * Unregister interest in events
         */
        UNREGISTER_EVENTS(Sequence.SHUTDOWN_PRE_EVENT_UNREGISTRATION),
        /**
         * Stop bus
         */
        STOP_BUS(Sequence.SHUTDOWN_POST_EVENT_UNREGISTRATION),
        /**
         * Any service specific shutdown action before the end
         */
        SHUTDOWN(Sequence.SHUTDOWN_POST_EVENT_UNREGISTRATION);

        public enum Sequence {
            STARTUP_PRE_EVENT_REGISTRATION,
            STARTUP_POST_EVENT_REGISTRATION,
            SHUTDOWN_PRE_EVENT_UNREGISTRATION,
            SHUTDOWN_POST_EVENT_UNREGISTRATION
        };

        private Sequence seq;

        LifecycleLevel(Sequence seq) {
            this.seq = seq;
        }

        public Sequence getSequence() {
            return seq;
        }

        //
        // Returns an ordered list of level for a particular sequence
        //
        public static List<LifecycleLevel> getLevelsForSequence(Sequence seq) {
            List<LifecycleLevel> result = Lists.newLinkedList();
            for (LifecycleLevel level : LifecycleLevel.values()) {
                if (level.getSequence() == seq) {
                    result.add(level);
                }
            }
            return result;
        }
    }

    public LifecycleLevel value();
}
