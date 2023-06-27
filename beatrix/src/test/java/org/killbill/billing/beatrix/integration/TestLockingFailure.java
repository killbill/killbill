/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.beatrix.integration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestLockingFailure extends TestIntegrationBase {

    @Inject
    private GlobalLocker locker;

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.putAll(DEFAULT_BEATRIX_PROPERTIES);
        allExtraProperties.put("org.killbill.payment.globalLock.retries", "10" /* Limits the timeout for payment system to 1 sec */);
        return getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testAccountLockingFailure() throws Exception {

        final LocalDate initialDate = new LocalDate(2023, 6, 1);
        clock.setDay(initialDate);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);
        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Grab lock for 3 sec in a different thread
        final OtherLocker other = new OtherLocker(locker, account.getId().toString(), 3);
        new Thread(other).start();

        // Remove AUTO_PAY_OFF, triggering the payment will fail as system cannot grab the global lock
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        // Wait for lock to be released
        other.waitUntilLockIsFree();

        // Check no payment was made, as the notification got rescheduled
        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext);
        Assert.assertEquals(0, payments.size());

        // Move time to 5' ahead, matching the first retry from default Period QueueRetryException.DEFAULT_RETRY_SCHEDULE
        // and verify payment was made
        busHandler.pushExpectedEvents(NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDeltaFromReality(1000 * 60 * 5);
        assertListenerStatus();
    }

    private static class OtherLocker implements Runnable {

        private final long holdLockTimeSec;
        private final Semaphore sem;

        private GlobalLock lock;

        public OtherLocker(final GlobalLocker locker, final String accountId, final long holdLockTimeSec) {
            this.holdLockTimeSec = holdLockTimeSec;
            this.sem = new Semaphore(0);
            try {
                lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), accountId, 30 /* does not matter, nobody holds it */);
            } catch (LockFailedException e) {
                Assert.fail("OtherLocker: test failed with LockFailedException ...");
            }
        }

        public void waitUntilLockIsFree() {
            try {
                sem.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            try {
                Thread.sleep(holdLockTimeSec * 1000);
            } catch (InterruptedException e) {
                Assert.fail("OtherLocker: interrupted exception...");
            } finally {
                if (lock != null) {
                    lock.release();
                }
                sem.release();
            }
        }
    }
}
