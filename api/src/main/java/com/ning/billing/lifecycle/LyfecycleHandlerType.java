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

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LyfecycleHandlerType {


    //
    // The level themselves are still work in progress depending on what we really need
    //
    public enum LyfecycleLevel {

        /**
         * Load and validate catalog (only for catalog subsytem)
         */
        LOAD_CATALOG(Sequence.STARTUP),
        /**
         * Initialize event bus (only for the event bus)
         */
        INIT_BUS(Sequence.STARTUP),
        /**
         * Service specific initalization
         */
        INIT_SERVICE(Sequence.STARTUP),
        /**
         * Service register their interest in events
         */
        REGISTER_EVENTS(Sequence.STARTUP),
        /**
         * Service start
         * - API call should not work
         * - Events might be triggered
         * - Batch processing jobs started
         */
        START_SERVICE(Sequence.STARTUP),
        /**
         * Stop service
         */
        STOP_SERVICE(Sequence.SHUTOWN),
        /**
         * Unregister interest in events
         */
        UNREGISTER_EVENTS(Sequence.SHUTOWN),
        /**
         * Stop bus
         */
        STOP_BUS(Sequence.SHUTOWN),
        /**
         * Any service specific shutdown action before the end
         */
        SHUTDOWN(Sequence.SHUTOWN);

        public enum Sequence {
            STARTUP,
            SHUTOWN
        };

        private Sequence seq;

        LyfecycleLevel(Sequence seq) {
            this.seq = seq;
        }

        public Sequence getSequence() {
            return seq;
        }
    }

    public LyfecycleLevel value();
}
