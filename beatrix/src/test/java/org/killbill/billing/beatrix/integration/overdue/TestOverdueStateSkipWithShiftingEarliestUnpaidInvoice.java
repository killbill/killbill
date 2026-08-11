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
 * The overdue reevaluation schedule is not re-anchored when the earliest unpaid invoice date changes,
 * so a configured state can be passed over entirely.
 *
 * <h3>The model these tests assume</h3>
 *
 * <p>The overdue system does not poll and recompute. It schedules the NEXT check for the moment the
 * NEXT state is expected to become due, and the documentation asks the operator to choose
 * {@code initialReevaluationInterval} and each {@code autoReevaluationInterval} so that every check
 * lands exactly on the following threshold. The configuration used here does exactly that:
 *
 * <pre>
 *   initialReevaluationInterval 5  -> day 5  == WARNING     (5)
 *   WARNING  autoReevaluationInterval 2 -> day 7  == BLOCKED      (7)
 *   BLOCKED  autoReevaluationInterval 4 -> day 11 == CANCELLATION (11)
 * </pre>
 *
 * <p>That alignment is computed from the earliest unpaid invoice date at the time the first check is
 * scheduled. Nothing recomputes it if that date later moves. When older invoices are paid and a newer
 * one is left unpaid, the anchor shifts forward, every scheduled check is left misaligned, and
 * {@code OverdueStateApplicator.apply()} compounds it by scheduling the next check relative to the
 * date the current check ran rather than to the new anchor.
 *
 * <h3>What is and is not claimed here</h3>
 *
 * <p>The defect is the missing re-anchoring. Severity-first matching in
 * {@code DefaultOverdueStateSet.calculateOverdueState()} is NOT treated as a defect: the docs state
 * that XML state order is significant and that the first state belongs at the bottom, and
 * {@code getFirstState()} returning the last element corroborates that. It is the mechanism by which
 * the missing re-anchoring becomes visible, not an independent fault - see
 * {@link #testDelayedCheckAppliesMostSevereMatchingState()}, which asserts that behaviour as correct.
 *
 * <h3>Structure</h3>
 *
 * <ul>
 *   <li>{@link #testWarningSkippedWhenEarliestUnpaidInvoiceShiftsForward()} - the defect. FAILS on
 *       current master.</li>
 *   <li>{@link #testControlProgressiveDegradationWithoutPartialPayment()} - same config, anchor never
 *       moves, correct progression. PASSES.</li>
 *   <li>{@link #testDelayedCheckAppliesMostSevereMatchingState()} - characterization of the
 *       evaluate-at-run-time behaviour. PASSES.</li>
 * </ul>
 *
 * <p>NOTE: {@code GuicyKillbillTestSuite} sets a {@code hasFailed} flag on the first failure in a
 * class, after which the remaining methods are reported as skipped. Run methods individually when
 * investigating.
 */
public class TestOverdueStateSkipWithShiftingEarliestUnpaidInvoice extends TestOverdueBase {

    private static final String WARNING = "WARNING";
    private static final String BLOCKED = "BLOCKED";
    private static final String CLEAR = OverdueWrapper.CLEAR_STATE_NAME;

    // Configuration reproduced verbatim from the client report. It is aligned to the thresholds
    // exactly as the documentation prescribes (see the class javadoc), which is what rules out
    // misconfiguration as an explanation: no choice of interval values prevents the failure, because
    // the alignment is invalidated by the anchor moving rather than by the intervals being wrong.
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
    // The defect
    // ---------------------------------------------------------------------------------------------

    @Test(groups = "slow", description = "Reevaluation schedule is not re-anchored when the earliest unpaid invoice date shifts, and WARNING is passed over")
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
        // Anchor is 05-31, and the schedule aligned to it is: 06-05 WARNING, 06-07 BLOCKED, 06-11 CANCELLATION.
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        record(timeline, statesObserved, "D+0  2012-05-31  recurring invoice unpaid (anchor = 05-31)");
        checkODState(CLEAR);

        // D+0 - second unpaid invoice, same day. Anchor unchanged.
        addExternalChargeThatFailsPayment("Charge A", new BigDecimal("500.00"));
        record(timeline, statesObserved, "D+0  2012-05-31  external charge 500 unpaid (anchor = 05-31)");

        // D+2 (2012-06-02) - third unpaid invoice. Anchor still 05-31, since 05-31 invoices are unpaid.
        addDaysAndCheckForCompletion(2);
        addExternalChargeThatFailsPayment("Charge B", new BigDecimal("700.00"));
        record(timeline, statesObserved, "D+2  2012-06-02  external charge 700 unpaid (anchor = 05-31)");

        // D+2 - THE PIVOT. Pay the two OLDEST invoices, leaving the 700 invoice (dated 06-02) unpaid.
        // The anchor moves 05-31 -> 06-02, so the correct schedule becomes 06-07 / 06-09 / 06-13.
        // The check already queued for 06-05 is not re-anchored to 06-07.
        paymentPlugin.makeAllInvoicesFailWithError(false);
        payTwoOldestUnpaidInvoices();
        paymentPlugin.makeAllInvoicesFailWithError(true);
        record(timeline, statesObserved, "D+2  2012-06-02  paid 2 oldest -> anchor MOVES to 06-02");
        checkODState(CLEAR);

        // D+3 (2012-06-03) - one more unpaid invoice. Anchor remains 06-02.
        addDaysAndCheckForCompletion(1);
        addExternalChargeThatFailsPayment("Charge C", new BigDecimal("200.00"));
        record(timeline, statesObserved, "D+3  2012-06-03  external charge 200 unpaid (anchor = 06-02)");

        // D+5 (2012-06-05) - the misaligned check fires. 3 days since the anchor, nothing matches, which
        // is correct. The defect is what it schedules next: 06-05 + 5 = 06-10, computed from the date the
        // check ran rather than from the anchor, which would have given 06-02 + 5 = 06-07.
        //
        // Landing exactly here and observing CLEAR also confirms the anchor really did move: had it still
        // been 05-31, 06-05 - 05-31 = 5 would have matched WARNING.
        advanceAndSettle(2);
        final String stateAtStaleCheck = record(timeline, statesObserved, "D+5  2012-06-05  misaligned check fires, no match, schedules 06-10");

        // D+7 (2012-06-07) - WARNING is due against the current anchor. Nothing is scheduled for this
        // date, so the account is still CLEAR. Recorded, not asserted - the run must continue.
        advanceAndSettle(2);
        final String stateAtWarningDue = record(timeline, statesObserved, "D+7  2012-06-07  WARNING DUE (anchor 06-02 + 5)");

        // D+10 (2012-06-10) - the check finally fires, 8 days after the anchor, past both WARNING and
        // BLOCKED. The most severe satisfied state is applied and WARNING is never entered.
        advanceAndSettle(3);
        final String stateAtRescheduledCheck = record(timeline, statesObserved, "D+10 2012-06-10  check fires (8 days after anchor)");

        // -----------------------------------------------------------------------------------------
        // Assertions collected so a single run reports the whole picture
        // -----------------------------------------------------------------------------------------
        final StringBuilder problems = new StringBuilder();

        // The scheduling defect: no check exists on the date WARNING became due against the new anchor.
        if (!WARNING.equals(stateAtWarningDue)) {
            problems.append("\n[A] SCHEDULE NOT RE-ANCHORED: on 2012-06-07 the account should be in WARNING - the earliest ")
                    .append("unpaid invoice is 2012-06-02 and WARNING is configured at 5 days - but it was '")
                    .append(stateAtWarningDue)
                    .append("'. The check at 2012-06-05 matched nothing and scheduled its successor as fireDate + ")
                    .append("initialReevaluationInterval (2012-06-05 + 5 = 2012-06-10) rather than re-anchoring on the ")
                    .append("earliest unpaid invoice date (2012-06-02 + 5 = 2012-06-07). No check exists on the date the ")
                    .append("configured threshold is reached.");
        }

        // The user-visible consequence.
        if (BLOCKED.equals(stateAtRescheduledCheck) && !statesObserved.contains(WARNING)) {
            problems.append("\n[B] CONSEQUENCE: the account went from CLEAR to BLOCKED on 2012-06-10 without ever entering ")
                    .append("WARNING. By then 8 days had elapsed since the earliest unpaid invoice, so both WARNING (>=5) and ")
                    .append("BLOCKED (>=7) were satisfied and the most severe was applied - correct in itself, but only ")
                    .append("reachable because the check did not run on 2012-06-07. No WARNING notification was emitted and ")
                    .append("no blocking_states row was written for it, so the customer was suspended with no grace period.");
        }

        // Guard: if neither fired and the account is not BLOCKED, the scenario stopped reproducing and
        // this test proves nothing - fail loudly rather than pass silently.
        if (problems.length() == 0 && !BLOCKED.equals(stateAtRescheduledCheck)) {
            problems.append("\n[!] SCENARIO DID NOT REPRODUCE: expected the account to be BLOCKED by 2012-06-10 (8 days after ")
                    .append("the earliest unpaid invoice) but it was '").append(stateAtRescheduledCheck)
                    .append("'. State at the misaligned check was '").append(stateAtStaleCheck)
                    .append("'. Check that the partial payment actually moved the earliest unpaid invoice date.");
        }

        assertTrue(problems.length() == 0, problems.toString() + renderTimeline(timeline, statesObserved));
    }

    // ---------------------------------------------------------------------------------------------
    // Control - anchor never moves, schedule stays aligned
    // ---------------------------------------------------------------------------------------------

    @Test(groups = "slow", description = "Control: with a fixed earliest unpaid invoice date the progression CLEAR -> WARNING -> BLOCKED is correct")
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

        // D+0 (2012-05-31) - a single unpaid invoice, never partially paid, so the anchor never moves and
        // every scheduled check stays aligned with its threshold.
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        record(timeline, statesObserved, "D+0  2012-05-31  recurring invoice unpaid (anchor = 05-31)");
        checkODState(CLEAR);

        // D+5 (2012-06-05) - WARNING: 05-31 + 5.
        addDaysAndCheckForCompletion(5, NextEvent.BLOCK);
        record(timeline, statesObserved, "D+5  2012-06-05  WARNING due");
        assertEquals(currentODState(), WARNING,
                     "Control scenario should reach WARNING on 2012-06-05." + renderTimeline(timeline, statesObserved));

        // D+7 (2012-06-07) - BLOCKED: 05-31 + 7, via WARNING's autoReevaluationInterval of 2.
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK, NextEvent.TAG);
        record(timeline, statesObserved, "D+7  2012-06-07  BLOCKED due");
        assertEquals(currentODState(), BLOCKED,
                     "Control scenario should reach BLOCKED on 2012-06-07." + renderTimeline(timeline, statesObserved));
    }

    // ---------------------------------------------------------------------------------------------
    // Characterization - NOT a defect, documents the assumption the defect above depends on
    // ---------------------------------------------------------------------------------------------

    /**
     * Documents that overdue conditions are evaluated at the time a check RUNS, not at the time it was
     * scheduled for, so a check that runs past several thresholds applies the most severe satisfied
     * state and passes over the intermediate ones.
     *
     * <p>This is asserted as CORRECT behaviour, not as a fault. The documentation states that XML state
     * order is significant and expects reevaluation intervals to be aligned so that checks land on their
     * thresholds; {@code DefaultOverdueStateSet.getFirstState()} returning the last element corroborates
     * the ordering convention. There is no documented guarantee that every state is entered when a check
     * arrives late.
     *
     * <p>It earns its place in this class for two reasons. It pins the behaviour that makes the
     * re-anchoring defect harmful rather than merely late - without it, a delayed check would still enter
     * WARNING and the consequence would be cosmetic. And it will start failing if anyone later changes
     * {@code calculateOverdueState()} to enforce sequential transitions, which is a decision that should
     * be made deliberately rather than as a side effect of fixing the scheduling.
     *
     * <p>The mechanism: {@code InternalCallContextFactory} defaults the context created date to
     * {@code clock.getUTCNow()}, and {@code OverdueWrapper.getNextOverdueState()} evaluates against
     * {@code context.toLocalDate(context.getCreatedDate())}.
     */
    @Test(groups = "slow", description = "Characterization: a check running past several thresholds applies the most severe satisfied state")
    public void testDelayedCheckAppliesMostSevereMatchingState() throws Exception {

        final List<String> timeline = new ArrayList<String>();
        final Set<String> statesObserved = new LinkedHashSet<String>();

        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        setupAccount();

        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(
                account.getId(), "externalKey", productName, ProductCategory.BASE, term,
                NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        // D+0 (2012-05-31) - one unpaid invoice. Anchor is fixed at 05-31 for the whole test and the only
        // check is scheduled for 06-05. No partial payment, so nothing here involves re-anchoring.
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        record(timeline, statesObserved, "D+0  2012-05-31  single unpaid invoice (anchor = 05-31)");
        checkODState(CLEAR);

        // Jump to D+8 (2012-06-08) in one step, past the 06-05 check. It then runs with 8 days elapsed,
        // satisfying WARNING (>= 5) and BLOCKED (>= 7) but not CANCELLATION (>= 11).
        advanceAndSettle(8);
        final String stateAfterDelayedCheck = record(timeline, statesObserved, "D+8  2012-06-08  delayed check runs (8 days after anchor)");

        assertEquals(stateAfterDelayedCheck, BLOCKED,
                     "A check running 8 days after the earliest unpaid invoice is expected to apply BLOCKED, the most "
                     + "severe satisfied state. If this now reports WARNING, sequential state transitions have been "
                     + "introduced in DefaultOverdueStateSet.calculateOverdueState() - a deliberate semantic change that "
                     + "should be reviewed on its own merits, not adopted as a side effect of a scheduling fix."
                     + renderTimeline(timeline, statesObserved));
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Move the clock and wait for the notification queues to drain, without asserting which bus events
     * arrived.
     *
     * <p>Deliberate: the event profile of the post-pivot steps changes once the scheduling defect is
     * fixed - WARNING starts firing at D+7, emitting a BLOCK event that does not occur on current
     * master. Pinning exact events there would make this test fail on FIXED code for the wrong reason.
     *
     * <p>{@code areAllNotificationsProcessed()} going true is NOT sufficient on its own. A state
     * transition drains from the notification queue first and only then posts BLOCK / TAG on the bus, so
     * a bare {@code busHandler.reset()} at that moment clears the flag before those events arrive and
     * they are recorded as unexpected immediately afterwards - which then trips the
     * {@code assertListenerStatus()} that {@code GuicyKillbillTestSuite.run()} performs after the test
     * method returns. {@code waitAndIgnoreEvents()} sleeps first and resets last, absorbing them.
     */
    private void advanceAndSettle(final int days) {
        clock.addDays(days);
        await().atMost(15, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return areAllNotificationsProcessed(internalCallContext.getTenantRecordId());
            }
        });
        // Absorb the bus events that follow queue drain (BLOCK, TAG on a state transition), then reset.
        busHandler.waitAndIgnoreEvents(3000);
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

        // Pay the two oldest only - the newest must stay unpaid so the anchor moves forward.
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
