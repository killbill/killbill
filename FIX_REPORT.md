# Fix Report — Issue #2208 (Park accounts on unrecoverable invoice processing failures)

## Root cause / design summary

This is an enhancement, not a bug. Before this change, `org.killbill.billing.invoice.InvoiceDispatcher`
and `org.killbill.billing.invoice.InvoiceListener` only parked an account in two situations:
when the invoicing system was disabled (`processAccountFromNotificationOrBusEvent`) and when an
`InvoiceApiException` with `UNEXPECTED_ERROR` was thrown inside `processAccountInternal`. Other
unrecoverable failure paths — lock-rescheduling not configured, `CatalogApiException` /
`AccountApiException` / `SubscriptionBaseApiException` in `processAccountInternal`, every
`InvoiceApiException` / `AccountApiException` / `RuntimeException` caught in the bus subscriber
actions of `InvoiceListener` — only logged at `WARN` and silently dropped the work, leaving the
account in a state where invoicing would never make progress with no human-visible signal.

## Change summary (by file)

- **`util/src/main/java/org/killbill/billing/util/config/definition/InvoiceConfig.java`** — added
  `isParkAccountsOnAllExceptions()` (both per-tenant and global), default `true`, configured by
  `org.killbill.invoice.parkAccountsOnAllExceptions` (per @sbrossie's request in the issue).

- **`invoice/src/main/java/org/killbill/billing/invoice/config/MultiTenantInvoiceConfig.java`** —
  implemented the new `isParkAccountsOnAllExceptions` overrides so per-tenant overrides work.

- **`invoice/src/main/java/org/killbill/billing/invoice/InvoiceDispatcher.java`**:
  - `processAccount()`: when `rescheduleProcessAccount()` returns `false` (no retry periods
    configured and we hit `LockFailedException` from a notification path), now parks the account.
    Updated WARN message to include the `parking account` marker.
  - `processAccountInternal()`: `CatalogApiException`, `AccountApiException`, and
    `SubscriptionBaseApiException` catch blocks now park the account when not in dry-run mode and
    the new config flag is true. Same `parking account` marker as the existing
    `UNEXPECTED_ERROR` branch.

- **`invoice/src/main/java/org/killbill/billing/invoice/InvoiceListener.java`**:
  - Constructor now injects `ParkedAccountsManager` and `InvoiceConfig`; field `accountApi` is
    promoted so the new helper can use it.
  - New public helper `handleFailedInvoiceDispatch(String, Long, Long, UUID, Exception)` — looks
    up the account by record id and parks it, swallowing everything (`AccountApiException`,
    `TagApiException`, `RuntimeException`) so it cannot escape a bus subscriber. When the new
    config flag is `false` it just logs.
  - Every bus subscriber `run()` block (`EffectiveSubscriptionInternalEvent`,
    `BlockingTransitionInternalEvent`, `InvoiceCreationInternalEvent`,
    `DefaultInvoiceAdjustmentEvent`, `RequestedSubscriptionInternalEvent`,
    `AccountChangeInternalEvent`) now routes its catch blocks through the helper and adds an
    outer `catch (RuntimeException)` so propagated runtime exceptions can no longer be silently
    dropped by the event bus.
  - `handleNextBillingDateEvent` and `handleEventForInvoiceNotification` also route through the
    helper. The public visibility of `handleFailedInvoiceDispatch` matches the issue's design so
    sibling components (e.g. `DefaultNextBillingDateNotifier`) can delegate to it once the
    `onRetriesExhausted` hook lands in killbill-commons.

- **`invoice/src/test/java/org/killbill/billing/invoice/TestInvoiceNotificationQListener.java`** —
  updated the test subclass to thread through the new constructor parameters.

- **`beatrix/src/test/java/org/killbill/billing/beatrix/integration/TestIntegrationBase.java`** —
  the inner `ConfigurableInvoiceConfig` test stub now overrides the new
  `isParkAccountsOnAllExceptions` methods (otherwise integration test compile breaks).

### What I deliberately did **not** do

Parts **B** and **D** of the proposal (`onRetriesExhausted` hook on `RetryableService`, override
in `DefaultNextBillingDateNotifier`) require modifying `killbill-commons`, which is a separate
repository. The issue itself flags this as a coordinated release. The `InvoiceListener` helper is
intentionally `public` and shaped to match the proposed override signature so a follow-up PR can
wire the upstream hook through without any further refactor here.

## How the test exercises the new behaviour

`invoice/src/test/java/org/killbill/billing/invoice/TestInvoiceListenerParking.java` is a new
Mockito-based fast test that constructs an `InvoiceListener` with mocked collaborators and
exercises `handleFailedInvoiceDispatch` directly:

1. **`testHandleFailedInvoiceDispatchParksAccountWhenConfigEnabled`** — sets
   `isParkAccountsOnAllExceptions` to `true`, fires a failed dispatch, and verifies
   `parkedAccountsManager.parkAccount(accountId, ...)` is invoked exactly once.
2. **`testHandleFailedInvoiceDispatchDoesNotParkWhenConfigDisabled`** — sets the config to
   `false` and verifies `parkAccount` is **never** called, proving the kill-switch works.
3. **`testHandleFailedInvoiceDispatchSurvivesAccountLookupFailure`** — makes
   `AccountInternalApi.getByRecordId` throw an `AccountApiException` and asserts the helper does
   not propagate any exception (subscriber actions must never re-throw).

The tests use `MockMakers.SUBCLASS` because Mockito's default inline mock maker fails to redefine
some bootstrap classes on Java 21+ (the JVM available in this sandbox is OpenJDK 25).

## Build status

`mvn -DskipTests -Dcheck.skip-spotbugs=true compile` — every module passes:

```
[INFO] killbill-util ...................................... SUCCESS
[INFO] killbill-tenant .................................... SUCCESS
[INFO] killbill-account ................................... SUCCESS
[INFO] killbill-catalog ................................... SUCCESS
[INFO] killbill-currency .................................. SUCCESS
[INFO] killbill-subscription .............................. SUCCESS
[INFO] killbill-entitlement ............................... SUCCESS
[INFO] killbill-junction .................................. SUCCESS
[INFO] killbill-usage ..................................... SUCCESS
[INFO] killbill-invoice ................................... SUCCESS
[INFO] killbill-overdue ................................... SUCCESS
[INFO] killbill-payment ................................... SUCCESS
[INFO] killbill-beatrix ................................... SUCCESS
[INFO] killbill-jaxrs ..................................... SUCCESS
[INFO] killbill-profiles-killbill ......................... SUCCESS
[INFO] killbill-profiles-killpay .......................... SUCCESS
[INFO] BUILD SUCCESS
```

`mvn -pl invoice -Dtest=TestInvoiceListenerParking -Dgroups=fast -Dcheck.skip-spotbugs=true test`:

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Full test-compile across the repo also succeeds (`mvn -DskipTests test-compile`).

## Confidence level

**Medium-high.**

What I'm confident in:

- Compile-clean across the whole repo, including all downstream modules (`beatrix`, `jaxrs`,
  `profiles-*`).
- The new fast unit tests actually execute and pass (3 / 3).
- The parking call sites match the design proposed in the issue, including the @sbrossie config
  switch and the consistent `parking account` log marker.
- Existing parking semantics are preserved (UNEXPECTED_ERROR still parks; dry-run never parks;
  parking is idempotent via `ParkedAccountsManager`).

Reasons it isn't "high":

- The slow integration tests (`TestInvoiceDispatcher`, beatrix `TestIntegration*`) require the
  ARM MySQL binary that is not available in this environment, so I didn't end-to-end exercise the
  `processAccountInternal` parking paths against a real DB.
- Parts B/D of the proposal (the `RetryableService.onRetriesExhausted` hook) are intentionally
  deferred to a coordinated killbill-commons release; the `RetryableException` retry-exhausted
  path still drops events silently for now. The public shape of
  `InvoiceListener.handleFailedInvoiceDispatch` is set up so the follow-up is a one-method
  override.
