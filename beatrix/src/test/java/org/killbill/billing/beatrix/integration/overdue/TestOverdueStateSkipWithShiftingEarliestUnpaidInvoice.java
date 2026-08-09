/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.overdue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Reproduces the "overdue state skip" defect reported by a client.
 *
 * <p>When an account carries several unpaid invoices and the OLDEST ones are paid while a NEWER one
 * remains unpaid, the date of the earliest unpaid invoice shifts forward. The overdue check already
 * scheduled against the OLD earliest-unpaid date is not recomputed against the new one.
 *
 * <p>Three code paths combine:
 *
 * <ol>
 *   <li>{@code OverdueListener.handlePaymentInfoEvent()} DOES react to the payment and DOES try to
 *       reschedule, but {@code OverdueCheckPoster.cleanupFutureNotificationsFormTransaction()} keeps
 *       whichever notification is EARLIEST - so the stale-but-earlier date wins over the
 *       fresh-but-later correct one, and the self-healing path is defeated.</li>
 *   <li>When the stale check fires and matches nothing, {@code OverdueStateApplicator.apply()}
 *       reschedules as {@code effectiveDate.plus(reevaluationInterval)} - "now + interval" - rather
 *       than against the date the next threshold actually becomes true. The next check therefore
 *       lands past the WARNING threshold.</li>
 *   <li>{@code DefaultOverdueStateSet.calculateOverdueState()} iterates states in XML order (most
 *       severe first) and returns the first match, so once BOTH WARNING and BLOCKED are satisfied it
 *       returns BLOCKED and never evaluates WARNING.</li>
 * </ol>
 *
 * <p>Net effect: CLEAR -&gt; BLOCKED, with no grace period and no WARNING notification.
 *
 * <h3>Why this test is structured the way it is</h3>
 *
 * <p>The test walks the ENTIRE timeline and asserts only at the end, so one run demonstrates both
 * symptoms rather than aborting on the first:
 *
 * <ul>
 *   <li><b>Symptom A (timing)</b> - nothing is evaluated on the date WARNING becomes due.</li>
 *   <li><b>Symptom B (skip)</b> - the account reaches BLOCKED having never passed through WARNING.</li>
 * </ul>
 *
 * <p>Symptom B is the defect the client actually reported; an earlier version of this test asserted
 * on Symptom A first and therefore never reached the step where B occurs.
 *
 * <p>Fixing the reschedule arithmetic alone is expected to resolve BOTH symptoms: if the check fires
 * on time at D+7, exactly 5 days have elapsed, so WARNING matches and BLOCKED (&gt;= 7) does not, and
 * the severity-first iteration never gets the chance to misbehave. This test therefore stays green
 * after that fix and remains a meaningful regression test for the skip itself.
 *
 * <p>The clock steps after the pivot deliberately do NOT assert on exact bus events, because the
 * event profile of those steps changes once the defect is fixed (WARNING starts firing at D+7).
 * Pinning events there would make this test fail on the FIXED code for the wrong reason. The
 * assertions are on observable overdue state instead, which is what the defect is about.
 *
 * <p>{@link #testControlProgressiveDegradationWithoutPartialPayment()} is the control: identical
 * config, no partial payment, correct CLEAR -&gt; WARNING -&gt; BLOCKED progression.
 */
public class TestOverdueStateSkipWithShiftingEarliestUnpaidInvoice extends TestOverdueBase {

    private static final String WARNING = "WARNING";
    private static final String BLOCKED = "BLOCKED";
    private static final String CLEAR = OverdueWrapper.CLEAR_STATE_NAME;

    // Overdue configuration reproduced verbatim from the client report.
    //
    // This config is load-bearing, not incidental. The defect depends on the relationship between
    // initialReevaluationInterval (5) and the two lowest thresholds (WARNING 5, BLOCKED 7): a 5-day
    // reschedule from the fire date overshoots the 2-day window in which WARNING is the only match.
    // With a smaller initialReevaluationInterval the check would land inside that window and no skip
    // would occur - which is why "lower the interval" is a usable workaround but not a fix.
    @Override
    public String getOverdueConfig() {
        return "<overdueConfig>" +
               "   <accountOverdueStates>" +
               "       <initialReevaluationInterval>" +
               "           <unit>DAYS</unit><number>5</number>" +
               "       </initialReevaluationInterval>" +
               "       <state name=\"CANCELLATION\">" +
               "           <condition>" +
               "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
               "                   <unit>DAYS</unit><number>11</number>" +
               "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
               "           </condition>" +
               "           <externalMessage>Reached CANCELLATION</externalMessage>" +
               "           <subscriptionCancellationPolicy>IMMEDIATE</subscriptionCancellationPolicy>" +
               "       </state>" +
               "       <state name=\"BLOCKED\">" +
               "           <condition>" +
               "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
               "                   <unit>DAYS</unit><number>7</number>" +
               "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
               "           </condition>" +
               "           <externalMessage>Reached BLOCKED</externalMessage>" +
               "           <blockChanges>true</blockChanges>" +
               "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
               "           <autoReevaluationInterval>" +
               "               <unit>DAYS</unit><number>4</number>" +
               "           </autoReevaluationInterval>" +
               "       </state>" +
               "       <state name=\"WARNING\">" +
               "           <condition>" +
               "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
               "                   <unit>DAYS</unit><number>5</number>" +
               "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
               "           </condition>" +
               "           <externalMessage>Reached WARNING</externalMessage>" +
               "           <blockChanges>true</blockChanges>" +
               "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
               "           <autoReevaluationInterval>" +
               "               <unit>DAYS</unit><number>2</number>" +
               "           </autoReevaluationInterval>" +
               "       </state>" +
               "   </accountOverdueStates>" +
               "</overdueConfig>";
    }

    // ---------------------------------------------------------------------------------------------
    // Repro
    // ---------------------------------------------------------------------------------------------

    @Test(groups = "slow", description = "WARNING is skipped when the earliest unpaid invoice date shifts forward after a partial payment")
    public void testWarningSkippedWhenEarliestUnpaidInvoiceShiftsForward() throws Exception {

        final List<String> timeline = new ArrayList<String>();
        final Set<String> statesObserved = new LinkedHashSet<String>();

        // 2012-05-01
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        setupAccount();

        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(
                account.getId(), "externalKey", productName, ProductCategory.BASE, term,
                NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        // D+0 (2012-05-31) - out of trial, first real invoice, payment fails.
        // Anchor: earliest unpaid = 05-31, and overdue schedules its first check for
        // 05-31 + initialReevaluationInterval(5) = 06-05.
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        record(timeline, statesObserved, "D+0  2012-05-31  recurring invoice unpaid (earliest = 05-31)");
        checkODState(CLEAR);

        // D+0 - second unpaid invoice, same day. Earliest unpaid still 05-31.
        addExternalChargeThatFailsPayment("Charge A", new BigDecimal("500.00"));
        record(timeline, statesObserved, "D+0  2012-05-31  external charge 500 unpaid (earliest = 05-31)");

        // D+2 (2012-06-02) - third unpaid invoice. Earliest unpaid still 05-31.
        addDaysAndCheckForCompletion(2);
        addExternalChargeThatFailsPayment("Charge B", new BigDecimal("700.00"));
        record(timeline, statesObserved, "D+2  2012-06-02  external charge 700 unpaid (earliest = 05-31)");

        // D+2 - THE PIVOT. Pay the two OLDEST invoices, leaving the 700 invoice (dated 06-02) unpaid.
        // Earliest unpaid shifts 05-31 -> 06-02, so the correct WARNING date becomes 06-02 + 5 = 06-07.
        // The already-scheduled 06-05 check is not recomputed: the payment event's attempt to
        // reschedule to 06-07 loses to the earlier stale 06-05 entry in OverdueCheckPoster.
        paymentPlugin.makeAllInvoicesFailWithError(false);
        payTwoOldestUnpaidInvoices();
        paymentPlugin.makeAllInvoicesFailWithError(true);
        record(timeline, statesObserved, "D+2  2012-06-02  paid 2 oldest -> earliest SHIFTS to 06-02");
        checkODState(CLEAR);

        // D+3 (2012-06-03) - one more unpaid invoice. Earliest unpaid remains 06-02.
        addDaysAndCheckForCompletion(1);
        addExternalChargeThatFailsPayment("Charge C", new BigDecimal("200.00"));
        record(timeline, statesObserved, "D+3  2012-06-03  external charge 200 unpaid (earliest = 06-02)");

        // D+5 (2012-06-05) - the stale check fires. 06-05 - 06-02 = 3 days, nothing matches, which is
        // correct. The defect is what happens next: reschedule is 06-05 + 5 = 06-10, not 06-02 + 5 = 06-07.
        //
        // Landing exactly here and seeing CLEAR is itself meaningful - it confirms the earliest unpaid
        // date really did shift. Had it still been 05-31, then 06-05 - 05-31 = 5 would have matched WARNING.
        advanceAndSettle(2);
        final String stateAtStaleCheck = record(timeline, statesObserved, "D+5  2012-06-05  stale check fires, no match, reschedules to 06-10");

        // D+7 (2012-06-07) - WARNING is due: 06-02 + 5. On unpatched code nothing is scheduled for this
        // date, so the account is still CLEAR. Recorded, NOT asserted - we need to keep going.
        //
        // Landing exactly on this date is what the client's own report could not do: they moved the
        // clock Aug 11 -> Aug 16 and never observed the WARNING due date, so their evidence could not
        // distinguish "rescheduled past it" from "scheduled correctly but fired late".
        advanceAndSettle(2);
        final String stateAtWarningDue = record(timeline, statesObserved, "D+7  2012-06-07  WARNING DUE (earliest 06-02 + 5)");

        // D+10 (2012-06-10) - the rescheduled check fires. 06-10 - 06-02 = 8 days, satisfying BOTH
        // WARNING (>= 5) and BLOCKED (>= 7). calculateOverdueState() returns BLOCKED on the first match.
        advanceAndSettle(3);
        final String stateAtRescheduledCheck = record(timeline, statesObserved, "D+10 2012-06-10  rescheduled check fires (8 days elapsed)");

        // -----------------------------------------------------------------------------------------
        // Assertions - collected so a single run reports both symptoms
        // -----------------------------------------------------------------------------------------
        final StringBuilder problems = new StringBuilder();

        // Symptom A - the reschedule arithmetic skipped over the WARNING due date entirely.
        if (!WARNING.equals(stateAtWarningDue)) {
            problems.append("\n[A] TIMING: account should be in WARNING on 2012-06-07 (earliest unpaid 2012-06-02 + 5 days) but was '")
                    .append(stateAtWarningDue)
                    .append("'. The check at 2012-06-05 found no match and rescheduled to fireDate + initialReevaluationInterval ")
                    .append("(2012-06-05 + 5 = 2012-06-10) instead of to 2012-06-07, so nothing evaluates the account on the date ")
                    .append("WARNING becomes true.");
        }

        // Symptom B - the reported defect: BLOCKED reached without ever passing through WARNING.
        if (BLOCKED.equals(stateAtRescheduledCheck) && !statesObserved.contains(WARNING)) {
            problems.append("\n[B] STATE SKIP: account reached BLOCKED on 2012-06-10 having never entered WARNING. ")
                    .append("At that point 8 days had elapsed since the earliest unpaid invoice, satisfying both WARNING (>=5) ")
                    .append("and BLOCKED (>=7); DefaultOverdueStateSet.calculateOverdueState() iterates most-severe-first and ")
                    .append("returned BLOCKED without evaluating WARNING. Progressive degradation was violated and the customer ")
                    .append("was suspended with no grace period and no WARNING notification.");
        }

        // Sanity: if neither symptom fired, the account must actually have progressed correctly.
        // Otherwise the scenario silently stopped reproducing anything and the test proves nothing.
        if (problems.length() == 0 && !BLOCKED.equals(stateAtRescheduledCheck)) {
            problems.append("\n[!] SCENARIO DID NOT REPRODUCE: expected the account to be BLOCKED by 2012-06-10 (8 days since ")
                    .append("the earliest unpaid invoice) but it was '").append(stateAtRescheduledCheck)
                    .append("'. State at the stale check was '").append(stateAtStaleCheck)
                    .append("'. Check that the partial payment actually shifted the earliest unpaid invoice date.");
        }

        assertTrue(problems.length() == 0, problems.toString() + renderTimeline(timeline, statesObserved));
    }

    // ---------------------------------------------------------------------------------------------
    // Control - same config, no partial payment, correct progression
    // ---------------------------------------------------------------------------------------------

    @Test(groups = "slow", description = "Control: without a partial payment the progression CLEAR -> WARNING -> BLOCKED is correct")
    public void testControlProgressiveDegradationWithoutPartialPayment() throws Exception {

        final List<String> timeline = new ArrayList<String>();
        final Set<String> statesObserved = new LinkedHashSet<String>();

        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        setupAccount();

        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(
                account.getId(), "externalKey", productName, ProductCategory.BASE, term,
                NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        // D+0 (2012-05-31) - single unpaid invoice, never partially paid, so the earliest unpaid date
        // stays 05-31 throughout and every scheduled check remains valid.
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        record(timeline, statesObserved, "D+0  2012-05-31  recurring invoice unpaid");
        checkODState(CLEAR);

        // D+5 (2012-06-05) - WARNING: 05-31 + 5.
        addDaysAndCheckForCompletion(5, NextEvent.BLOCK);
        record(timeline, statesObserved, "D+5  2012-06-05  WARNING due");
        assertEquals(currentODState(), WARNING,
                     "Control scenario should reach WARNING on 2012-06-05." + renderTimeline(timeline, statesObserved));

        // D+7 (2012-06-07) - BLOCKED: 05-31 + 7, reached via WARNING's autoReevaluationInterval of 2.
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK, NextEvent.TAG);
        record(timeline, statesObserved, "D+7  2012-06-07  BLOCKED due");
        assertEquals(currentODState(), BLOCKED,
                     "Control scenario should reach BLOCKED on 2012-06-07." + renderTimeline(timeline, statesObserved));
    }

    // ---------------------------------------------------------------------------------------------
    // Isolation - does the state skip occur WITHOUT the reschedule defect?
    // ---------------------------------------------------------------------------------------------

    /**
     * Isolates the severity-first matching defect from the reschedule defect.
     *
     * <p>There is no partial payment here and the earliest unpaid invoice date never moves, so the
     * FIRST scheduled check (D+0 + initialReevaluationInterval = D+5) is never stale and no reschedule
     * ever takes place. The only thing that differs from the control is that the clock jumps past that
     * check in a single step, so it fires late.
     *
     * <p>This is possible because {@code OverdueWrapper.getNextOverdueState()} evaluates against
     * {@code context.toLocalDate(context.getCreatedDate())} - i.e. the moment the check actually runs -
     * not the date the notification was scheduled for. A check delayed for ANY reason therefore
     * evaluates against the later date, where several thresholds may be satisfied at once.
     *
     * <p>In production the equivalent causes are a backed-up notification queue, a node down over a
     * weekend, or a tenant paused and resumed - none of which involve the reschedule arithmetic.
     *
     * <p>Interpreting the result:
     * <ul>
     *   <li><b>Fails (CLEAR -&gt; BLOCKED)</b> - the severity-first match is an independent defect,
     *       reachable without the reschedule bug. It warrants its own issue and its own fix, and the
     *       question of whether a delayed check should ever jump states becomes a product decision.</li>
     *   <li><b>Passes</b> - the skip is only reachable via the reschedule path, so fixing the
     *       reschedule arithmetic is the whole fix and sequential enforcement is unnecessary.</li>
     * </ul>
     */
    @Test(groups = "slow", description = "Isolation: does a check delayed by something OTHER than the reschedule bug also skip WARNING?")
    public void testStateSkipFromDelayedCheckWithoutRescheduleDefect() throws Exception {

        final List<String> timeline = new ArrayList<String>();
        final Set<String> statesObserved = new LinkedHashSet<String>();

        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        setupAccount();

        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(
                account.getId(), "externalKey", productName, ProductCategory.BASE, term,
                NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        // D+0 (2012-05-31) - one unpaid invoice. Earliest unpaid = 05-31 and stays there for the whole
        // test. First (and only) check is scheduled for 05-31 + 5 = 06-05, and is never rescheduled.
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        record(timeline, statesObserved, "D+0  2012-05-31  single unpaid invoice (earliest = 05-31)");
        checkODState(CLEAR);

        // Jump straight to D+8 (2012-06-08) in ONE step, past the 06-05 check. That check now runs with
        // 8 days elapsed - the same elapsed time as the client's reported Aug 16 evaluation - satisfying
        // both WARNING (>= 5) and BLOCKED (>= 7), and still short of CANCELLATION (>= 11).
        advanceAndSettle(8);
        final String stateAfterDelayedCheck = record(timeline, statesObserved, "D+8  2012-06-08  delayed first check fires (8 days elapsed)");

        assertTrue(!BLOCKED.equals(stateAfterDelayedCheck) || statesObserved.contains(WARNING),
                   "\n[C] INDEPENDENT STATE SKIP: a check delayed by clock movement alone - no partial payment, no "
                   + "stale notification, no reschedule - still took the account straight to BLOCKED without passing "
                   + "through WARNING. At 8 days elapsed both WARNING (>=5) and BLOCKED (>=7) were satisfied and "
                   + "DefaultOverdueStateSet.calculateOverdueState() returned the most severe match. This means the "
                   + "severity-first matching defect is reachable independently of the reschedule arithmetic, and "
                   + "fixing the reschedule alone would leave it latent."
                   + renderTimeline(timeline, statesObserved));
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Move the clock and wait for the notification queues to drain, WITHOUT asserting which bus
     * events arrived.
     *
     * <p>Deliberate: the event profile of the post-pivot steps changes once the defect is fixed -
     * WARNING starts firing at D+7, emitting a BLOCK event that does not occur on unpatched code. A
     * test that pinned exact events there would fail on the FIXED code for the wrong reason. The bus
     * handler is reset afterwards so those unasserted events do not trip a later assertListenerStatus().
     */
    private void advanceAndSettle(final int days) {
        clock.addDays(days);
        await().atMost(15, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return areAllNotificationsProcessed(internalCallContext.getTenantRecordId());
            }
        });
        busHandler.reset();
    }

    private void addExternalChargeThatFailsPayment(final String description, final BigDecimal amount) throws Exception {
        final LocalDate today = clock.getUTCToday();
        final InvoiceItem charge = new ExternalChargeInvoiceItem(null, account.getId(), bundle.getId(), description,
                                                                 today, today.plusMonths(1), amount, Currency.USD, null);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // autoCommit = true: a DRAFT invoice is invisible to the overdue system and reproduces nothing.
        invoiceUserApi.insertExternalCharges(account.getId(), today, List.of(charge), true, null, callContext);
        assertListenerStatus();
    }

    private void payTwoOldestUnpaidInvoices() throws Exception {
        final List<Invoice> unpaid = new ArrayList<Invoice>(
                invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), null, clock.getUTCToday(), callContext));
        unpaid.sort((a, b) -> a.getInvoiceDate().compareTo(b.getInvoiceDate()));

        assertTrue(unpaid.size() >= 3,
                   "Expected at least 3 unpaid invoices before the partial payment, found " + unpaid.size());

        // Pay the two oldest only - the newest must stay unpaid so the earliest-unpaid date shifts.
        createExternalPaymentAndCheckForCompletion(account, unpaid.get(0), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        createExternalPaymentAndCheckForCompletion(account, unpaid.get(1), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
    }

    private String currentODState() {
        final BlockingState state = blockingApi.getBlockingStateForService(account.getId(), BlockingStateType.ACCOUNT,
                                                                          OverdueService.OVERDUE_SERVICE_NAME, internalCallContext);
        return state != null ? state.getStateName() : CLEAR;
    }

    private String record(final List<String> timeline, final Set<String> statesObserved, final String label) {
        final String state = currentODState();
        statesObserved.add(state);
        timeline.add(String.format("%-58s -> %s", label, state));
        return state;
    }

    private String renderTimeline(final List<String> timeline, final Set<String> statesObserved) {
        final StringBuilder sb = new StringBuilder("\n\n--- overdue timeline ---\n");
        timeline.forEach(line -> sb.append(line).append("\n"));
        sb.append("states observed: ").append(statesObserved).append("\n");
        sb.append("------------------------\n");
        return sb.toString();
    }
}
