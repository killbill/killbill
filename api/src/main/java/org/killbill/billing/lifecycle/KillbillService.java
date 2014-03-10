/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.lifecycle;

/**
 * The interface <code>KillbillService<code/> represents a service that will go through the Killbill lifecyle.
 * <p>
 * A <code>KillbillService<code> can register handlers for the various phases of the lifecycle, so
 * that its proper initialization/shutdown sequence occurs at the right time with regard
 * to other <code>KillbillService</code>.
 *
 */
public interface KillbillService {

    public static class ServiceException extends Exception {

        private static final long serialVersionUID = 176191207L;

        public ServiceException() {
            super();
        }

        public ServiceException(final String msg, final Throwable e) {
            super(msg, e);
        }

        public ServiceException(final String msg) {
            super(msg);
        }

        public ServiceException(final Throwable msg) {
            super(msg);
        }
    }

    /**
     * @return the name of the service
     */
    public String getName();


}
