package com.ning.billing.invoice.notification;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.UUID;

public class MockNextBillingDatePoster implements NextBillingDatePoster {
    @Override
    public void insertNextBillingNotification(Transmogrifier transactionalDao, UUID subscriptionId, DateTime futureNotificationTime) {
        // do nothing
    }
}
