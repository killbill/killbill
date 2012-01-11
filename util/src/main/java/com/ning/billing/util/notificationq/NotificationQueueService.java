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


public interface NotificationQueueService {

    public interface NotificationQueueHandler {
        /**
         * Called when the Notification thread has been started
         */
        public void completedQueueStart();

        /**
         * Called for each notification ready
         *
         * @param key the notification key associated to that notification entry
         */
        public void handleReadyNotification(String notificationKey);
        /**
         * Called right before the Notification thread is about to exit
         */
        public void completedQueueStop();
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
     */
    NotificationQueue createNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler, final NotificationConfig config);
}
