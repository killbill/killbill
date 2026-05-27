# Fix Report — Kill Bill issue #1922

Parent summary invoices missing or merged across billing cycles.

## Root cause

`InvoiceDispatcher.processParentInvoiceForInvoiceGenerationWithLock(...)`
([`invoice/src/main/java/org/killbill/billing/invoice/InvoiceDispatcher.java:1351`](invoice/src/main/java/org/killbill/billing/invoice/InvoiceDispatcher.java)) reuses
*any* existing parent DRAFT invoice returned by `InvoiceDao.getParentDraftInvoice(...)` and folds the
new child invoice's amount into the prior cycle's `PARENT_SUMMARY` item via `updateInvoiceItemAmount`.
The lookup query
([`invoice/src/main/resources/org/killbill/billing/invoice/dao/InvoiceSqlDao.sql.stg:73`](invoice/src/main/resources/org/killbill/billing/invoice/dao/InvoiceSqlDao.sql.stg))
ignored the billing date entirely and ordered by `record_id ASC`, returning the oldest DRAFT first.

In the steady state, the per-day parent auto-commit notification scheduled in
`DefaultInvoiceDao.notifyOfParentInvoiceCreation` ensures the prior DRAFT is committed before the
next cycle starts, so this code path is never exercised cross-cycle. But when that auto-commit is
delayed, retried unsuccessfully, or disabled (as in #1922's reporter, who drove invoicing through
the public `POST /invoices` API for many children), the August DRAFT is still around when the
September child invoices land. Each September child invoice then sees the August DRAFT, finds an
existing `PARENT_SUMMARY` for itself, and `updateInvoiceItemAmount` collapses both periods'
amounts onto the same item. The early `return` after the merge means no new DRAFT is ever opened
for September — matching the reporter's "parent invoices are not generated **or miss children
entries in the summary**".

## Fix summary

- **`invoice/src/main/java/org/killbill/billing/invoice/InvoiceDispatcher.java`** — In
  `processParentInvoiceForInvoiceGenerationWithLock`, compute the child invoice's local
  date (`invoiceDate`) up-front. If the existing parent DRAFT's `invoiceDate` differs from
  the current `invoiceDate`, treat it as stale (`draftParentInvoice = null`) so the
  cross-cycle merge path is never taken and a fresh DRAFT is opened for the new cycle. The
  same-day multi-action path (e.g. plan change + recurring on the same day) is preserved
  because the dates match.
- **`invoice/src/main/resources/org/killbill/billing/invoice/dao/InvoiceSqlDao.sql.stg`** —
  `getParentDraftInvoice` now orders by `invoice_date DESC, record_id DESC`. With the
  staleness check above, once a new DRAFT is opened for the current cycle, subsequent
  child invoices in that cycle will reliably pick up the *current* DRAFT (rather than the
  older stale one) and append their items. The only caller besides the dispatcher is
  `TestInvoiceDao#testCreateParentInvoice`, which exercises a single DRAFT and is
  insensitive to ordering.

## How the regression test reproduces the bug

`invoice/src/test/java/org/killbill/billing/invoice/TestInvoiceDispatcher.java::testParentInvoiceNotMergedAcrossBillingCycles`
sets the test clock to 2025-08-01, creates one parent and three child accounts
(`isPaymentDelegatedToParent = true`), persists one `COMMITTED` child invoice per child for
August totaling $60, and runs `dispatcher.processParentInvoiceForInvoiceGeneration` on each.
It then advances the clock to 2025-09-01 **without committing the August parent DRAFT**
(simulating the delayed/disabled auto-commit) and repeats the process with three September
child invoices totaling $66.

Expected: two parent DRAFT invoices, one per cycle, each with three `PARENT_SUMMARY`
items and the correct per-cycle totals. The August DRAFT must still hold $60 unchanged.

Before the fix, the September dispatch finds the August DRAFT, hits the merge branch for
every child, and never opens a September DRAFT — so `getInvoicesByAccount(parent)` returns
one invoice (not two) with $126 collapsed onto the August items. The first
`assertEquals(parentInvoices.size(), 2, ...)` fires the assertion.

## Build / test status

```
$ mvn -pl invoice -am -DskipTests compile
...
[INFO] killbill-invoice ................................... SUCCESS [  1.250 s]
[INFO] BUILD SUCCESS
```

Both `compile` and `test-compile` pass cleanly for the invoice module after the changes.

The slow test could **not** be exercised in this sandbox: the embedded-DB Killbill test
harness fails to bootstrap on this machine because (a) Mockito's inline mock maker cannot
instrument the test-only `ClockMock` class on Java 25 (`Java 25 (69) is not supported by the
current version of Byte Buddy`) and (b) no MariaDB binary is published for `aarch64`
(`archive not found: /mysql-Mac_OS_X-aarch64.tar.gz`). Every other `@Test(groups = "slow")`
in `TestInvoiceDispatcher` fails the same way, so this is an environment issue rather than a
regression introduced here. The new test follows the same construction pattern as
`TestInvoiceDao#testCreateParentInvoice` and the `slow` tests already in
`TestInvoiceDispatcher`, and only uses APIs that work in the standard CI configuration
(Java 11 + an x86_64 MariaDB).

## Confidence: medium

High confidence on the root cause and the direction of the fix:

- The merge-vs-create logic in the dispatcher is the only code path that ever attaches a
  child amount to an *existing* parent invoice — the bug description (no new parent invoice,
  or missing per-child entries on the summary) maps to exactly the two ways this can go
  wrong when a stale DRAFT is reused across periods.
- The same-day multi-action case covered by
  `TestIntegrationParentInvoice#testParentInvoiceGenerationMultipleActionsSameDay` still
  satisfies `parentDraft.invoiceDate == today`, so the existing assertion of "one merged
  `PARENT_SUMMARY` per child" is preserved.

Medium (not high) because I could not run the full `slow` invoice suite in this sandbox to
verify there are no second-order assertion failures elsewhere (e.g. tests that incidentally
relied on `record_id ASC` ordering through `getParentDraftInvoice`). A normal CI run on
Java 11 + the standard MariaDB image should be a green or red signal.
