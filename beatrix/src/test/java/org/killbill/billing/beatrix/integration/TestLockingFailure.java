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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.billing.util.queue.QueueRetryException;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

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



    //
    // Emulate a scenario where Kill Bill fails to grab the Account lock when processing
    // an InvoiceCreationInternalEvent event in the PaymentBusEventHandler
    //
    // We want to check that bus event is rescheduled and payment goes through on the next rescheduling.
    //
    @Test(groups = "slow")
    public void testPaymentLockingFailure() throws Exception {

        final LocalDate initialDate = new LocalDate(2023, 6, 1);
        clock.setDay(initialDate);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // Simulate a LockFailedException by having the payment plugin wrap the exception inside a PaymentPluginApiException
        // (This is the only way we can simulate such exception without having to hack our payment code)
        //
        // Our code knows how to un wrap and catch it as LockFailedException
        paymentPlugin.makeNextPaymentFailWithLockFailedException();

        // The INVOICE_PAYMENT_ERROR, PAYMENT_PLUGIN_ERROR and payment created are a side effect of using the payment plugin as a way to bubble up the LockFailedException
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.PAYMENT_PLUGIN_ERROR);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);
        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Check no payment was made, as the notification got rescheduled
        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext);
        Assert.assertEquals(1, payments.size());
        Assert.assertEquals(1, payments.get(0).getTransactions().size());
        Assert.assertEquals(TransactionStatus.UNKNOWN, payments.get(0).getTransactions().get(0).getTransactionStatus());

        // Fix UNKNOWN payment side prior we attempt the retry otherwise payment code refuses to make the payment.
        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR);
        adminPaymentApi.fixPaymentTransactionState(payments.get(0), payments.get(0).getTransactions().get(0), TransactionStatus.PAYMENT_FAILURE, null, null, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Move ahead by 30' to match default payment config - add 5 for safety
        busHandler.pushExpectedEvents(NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDeltaFromReality(1000 * 35);
        assertListenerStatus();
    }

    //
    // Emulate a scenario where Kill Bill fails to grab the Account lock when processing
    // the removal AUTO_PAY_OFF tag
    //
    // We want to check that bus event is rescheduled and payment goes through on the next rescheduling.
    //
    @Test(groups = "slow")
    public void testPaymentLockingFailure_AUTO_PAY_OFF() throws Exception {

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

        // Move ahead by 30' to match default payment config - add 5 for safety
        // and verify payment was made
        busHandler.pushExpectedEvents(NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDeltaFromReality(1000 * 35);
        assertListenerStatus();
    }


    @Test(groups = "slow")
    public void testOverdueLockingFailure() throws Exception {

        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>5</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>5</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";

        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        final LocalDate initialDate = new LocalDate(2023, 6, 1);
        clock.setDay(initialDate);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        paymentPlugin.makeNextPaymentFailWithError();
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);
        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // First overdue event 5 days after payment failure as configured
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(5);
        assertListenerStatus();

        // Grab lock for 3 sec in a different thread
        final OtherLocker other = new OtherLocker(locker, account.getId().toString(), 3);
        new Thread(other).start();

        // Payment retry is scheduled 8 days after payment failure, so 3 days after OD1
        // Because we hold the global lock, this is rescheduled and we do not see any events.
        clock.addDays(3);
        other.waitUntilLockIsFree();
        assertListenerStatus();

        // Move ahead by 30' to match default overdue config - add 5 for safety
        // and verify overdue state is cleared and payment went through.
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDeltaFromReality(1000 * 35);
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
