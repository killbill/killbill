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

package com.ning.billing.ovedue.notification;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;

public class DefaultOverdueCheckPoster implements OverdueCheckPoster {
    private final static Logger log = LoggerFactory.getLogger(DefaultOverdueCheckNotifier.class);

	private final NotificationQueueService notificationQueueService;

	@Inject
    public DefaultOverdueCheckPoster(
			NotificationQueueService notificationQueueService) {
		super();
		this.notificationQueueService = notificationQueueService;
	}

	@Override
	public void insertOverdueCheckNotification(final Transmogrifier transactionalDao, final Blockable overdueable, final DateTime futureNotificationTime) {
    	NotificationQueue checkOverdueQueue;
		try {
			checkOverdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
					DefaultOverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);
			 log.info("Queuing overdue check notification. id: {}, timestamp: {}", overdueable.getId().toString(), futureNotificationTime.toString());

	            checkOverdueQueue.recordFutureNotificationFromTransaction(transactionalDao, futureNotificationTime, new NotificationKey(){
	                @Override
	                public String toString() {
	                    return overdueable.getId().toString();
	                }
	    	    });
		} catch (NoSuchNotificationQueue e) {
			log.error("Attempting to put items on a non-existent queue (DefaultOverdueCheck).", e);
		}
    }
	
	public void clearNotificationEventsFor(final Blockable overdueable) {
	    NotificationQueue checkOverdueQueue;
        try {
            checkOverdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                DefaultOverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);
            checkOverdueQueue.removeNotificationsByKey(overdueable.getId());
        } catch (NoSuchNotificationQueue e) {
            log.error("Attempting to clear items from a non-existent queue (DefaultOverdueCheck).", e);
        }
	}
}
