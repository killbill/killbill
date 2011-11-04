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

import com.ning.billing.config.IBusinessConfig;
import com.ning.billing.config.IKillbillConfig;

public interface IService {

    public static class ServiceException extends Exception  {

        private static final long serialVersionUID = 176191207L;

        public ServiceException() {
            super();
        }

        public ServiceException(String msg, Throwable e) {
            super(msg, e);
        }

        public ServiceException(String msg) {
            super(msg);
        }

        public ServiceException(Throwable msg) {
            super(msg);
        }
    }

    /**
     *
     * @param businessConfig business configuration
     * @param killbillConfig service detail configuration
     * @throws ServiceException
     *
     * Initialize the service prior to start
     */
    public void initialize(IBusinessConfig businessConfig, IKillbillConfig killbillConfig)
        throws ServiceException;

    /**
     *
     * @throws ServiceException
     *
     * Start the given service
     */
    public void start()
        throws ServiceException;

    /**
     * Stop the given service
     *
     * @throws ServiceException
     */
    public void stop()
        throws ServiceException;

}
