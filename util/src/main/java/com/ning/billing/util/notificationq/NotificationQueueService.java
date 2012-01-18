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

package com.ning.billing.util.notificationq;

import java.util.NoSuchElementException;


public interface NotificationQueueService {

    public interface NotificationQueueHandler {
        /**
         * Called for each notification ready
         *
         * @param key the notification key associated to that notification entry
         */
        public void handleReadyNotification(String notificationKey);
     }

    public static final class NotficationQueueAlreadyExists extends Exception {
        private static final long serialVersionUID = 1541281L;

        public NotficationQueueAlreadyExists(String msg) {
            super(msg);
        }
    }

    public static final class NoSuchNotificationQueue extends Exception {
        private static final long serialVersionUID = 1541281L;

        public NoSuchNotificationQueue(String msg) {
            super(msg);
        }
    }

    /**
     * Creates a new NotificationQueue for a given associated with the given service and queueName
     *
     * @param svcName the name of the service using that queue
     * @param queueName a name for that queue (unique per service)
     * @param handler the handler required for notifying the caller of state change
     * @param config the notification queue configuration
     *
     * @return a new NotificationQueue
     *
     * @throws NotficationQueueAlreadyExists is the queue associated with that service and name already exits
     *
     */
    NotificationQueue createNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler, final NotificationConfig config)
        throws NotficationQueueAlreadyExists;

    /**
     * Retrieves an already created NotificationQueue by service and name if it exists
     *
     * @param svcName
     * @param queueName
     * @return
     *
     * @throws NoSuchNotificationQueue if queue does not exist
     */
    NotificationQueue getNotificationQueue(final String svcName, final String queueName)
        throws NoSuchNotificationQueue;

}
