# Issue #2208 — Park accounts on unrecoverable invoice processing failures

## Root cause / design summary

Invoice processing has multiple silent-failure paths that abandon an account without parking
it, so the operator never finds out. The relevant sites are concentrated in:

- `org.killbill.billing.invoice.InvoiceDispatcher#processAccount` — when
  `rescheduleProcessAccount()` returns `false` (no reschedule periods configured) after a
  `LockFailedException`, the account is logged at WARN and dropped.
- `org.killbill.billing.invoice.InvoiceDispatcher#processAccountInternal` — when
  `CatalogApiException` / `AccountApiException` / `SubscriptionBaseApiException` bubbles up
  from `billingApi.getBillingEventsForAccountAndUpdateAccountBCD(...)`, the catch blocks
  return `Collections.emptyList()` after a WARN, again losing the failure.
- `org.killbill.billing.invoice.InvoiceListener` — the `SubscriberAction.run()` lambdas for
  `EffectiveSubscriptionInternalEvent`, `BlockingTransitionInternalEvent`,
  `InvoiceCreationInternalEvent`, `DefaultInvoiceAdjustmentEvent`,
  `RequestedSubscriptionInternalEvent` and `AccountChangeInternalEvent` either WARN-and-swallow
  the recoverable-looking checked exceptions or let `RuntimeException`s propagate to the bus
  where they are dropped.

The fix routes all of these paths through `ParkedAccountsManager.parkAccount(...)` so the
account ends up tagged with `SystemTags.PARK_TAG_DEFINITION_ID` and the operator can find it.
Per the reviewer comment on the issue, parking is gated by a new tenant-aware config flag
`org.killbill.invoice.parkAccountsOnAllExceptions` (default `true`), and every park log line
uses the shared WARN marker `parking account` so alerting can pivot on it.

## Change summary

| File | Change |
| --- | --- |
| `util/.../config/definition/InvoiceConfig.java` | New `isParkAccountsOnAllExceptions()` static and tenant-aware accessors, default `true`. |
| `invoice/.../config/MultiTenantInvoiceConfig.java` | Implementations of both new accessors with the standard `getStringTenantConfig` fallback to the static value. |
| `invoice/.../InvoiceDispatcher.java` | `processAccount` parks on `LockFailedException` when `rescheduleProcessAccount` returns false (Section A of the issue). `processAccountInternal` parks (non dry-run, when config enabled) on `CatalogApiException` / `AccountApiException` / `SubscriptionBaseApiException` (Section F). New `parkAccountOnRecoverableError` helper. All WARN messages now carry the `parking account` marker. |
| `invoice/.../InvoiceListener.java` | Injects `ParkedAccountsManager` and `InvoiceConfig`; stores `accountApi` as a field. Each `SubscriberAction.run()` now also catches `RuntimeException` and routes every failure through a new private `handleFailedInvoiceDispatch(...)` helper that resolves the account from `searchKey1` and parks it (Section E). Adds a public `onRetriesExhaustedForInvoice(QueueEvent, Long, Long)` method that performs the same park; it is left non-`@Override` so it compiles before the killbill-commons `RetryableService` hook (Section B) lands. |
| `invoice/.../notification/DefaultNextBillingDateNotifier.java` | New public `onRetriesExhaustedForInvoice(...)` that simply delegates to `InvoiceListener` (Section D), again non-`@Override` so it compiles today and snaps into place when the parent hook ships. |
| `invoice/.../TestInvoiceNotificationQListener.java` (test) | Constructor updated to forward `ParkedAccountsManager` and `InvoiceConfig` to the new `InvoiceListener` signature. |
| `invoice/.../TestInvoiceDispatcherParkingOnFailure.java` (test, new) | Four `groups="slow"` tests covering the three `processAccountInternal` exception paths plus the dry-run negative case. |

### What is intentionally NOT done here

- **Section B (`RetryableService.scheduleRetry` hook).** That class lives in the separate
  `killbill-commons` repository (the issue explicitly notes "requires a coordinated release").
  This branch ships the consumer side — `onRetriesExhaustedForInvoice` on both `InvoiceListener`
  and `DefaultNextBillingDateNotifier` — so the killbill-commons PR only has to add the
  `protected void onRetriesExhausted(QueueEvent, Long, Long)` no-op and the `scheduleRetry()`
  callsite invocation, plus an `@Override` on the two consumer methods.

## How the test exercises the new behaviour

`TestInvoiceDispatcherParkingOnFailure` (in `invoice/src/test/java/org/killbill/billing/invoice/`)
extends `InvoiceTestSuiteWithEmbeddedDB` and uses the existing Mockito-bound `BillingInternalApi`
to make `getBillingEventsForAccountAndUpdateAccountBCD(...)` throw each of the three checked
exception types in turn. It then drives `dispatcher.processAccountFromNotificationOrBusEvent(...)`
and asserts via `tagUserApi.getTagsForAccount(...)` that:

1. `CatalogApiException` in non-dryRun → exactly one tag, `PARK_TAG_DEFINITION_ID`.
2. `CatalogApiException` in dryRun → no tags (regression guard for accidental side-effects).
3. `AccountApiException` in non-dryRun → parked.
4. `SubscriptionBaseApiException` in non-dryRun → parked.

The first test would have failed before this change because `processAccountInternal` returned
`Collections.emptyList()` without tagging anything.

## Build status

`mvn -pl invoice -am -DskipTests compile`:

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS
[INFO] killbill-api ....................................... SUCCESS
[INFO] killbill-util ...................................... SUCCESS
[INFO] killbill-tenant .................................... SUCCESS
[INFO] killbill-account ................................... SUCCESS
[INFO] killbill-catalog ................................... SUCCESS
[INFO] killbill-subscription .............................. SUCCESS
[INFO] killbill-entitlement ............................... SUCCESS
[INFO] killbill-junction .................................. SUCCESS
[INFO] killbill-usage ..................................... SUCCESS
[INFO] killbill-invoice ................................... SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

`mvn -pl invoice -am -DskipTests test-compile`: BUILD SUCCESS (all 11 modules).

Full-project `mvn -DskipTests compile`: BUILD SUCCESS across all 19 reactor modules
(including `beatrix`, `jaxrs`, `profiles-killbill`, `profiles-killpay`).

The embedded MySQL `slow` tests cannot start on this ARM host
(`archive not found: /mysql-Mac_OS_X-aarch64.tar.gz`), which is an environment limitation
called out by the prompt and not within the scope of this fix.

## Confidence level

**Medium-high.**

What I am confident about:
- The three call-sites changed in `InvoiceDispatcher` are exactly the ones the issue
  identifies, and the new code reuses the existing `parkAccount()` helper that has been
  battle-tested for the `UNEXPECTED_ERROR` path.
- The `InvoiceListener` constructor change is mechanically consistent — all callers
  (`TestInvoiceNotificationQListener`, Guice DI via `TestInvoiceModuleWithEmbeddedDb`,
  production wiring via `InvoiceModule`) compile cleanly.
- The new config accessor mirrors the structure of the existing `isInvoicingSystemEnabled`
  pair, including the `MultiTenantInvoiceConfig` override.

Where caveats apply:
- `onRetriesExhaustedForInvoice` cannot become an `@Override` until the killbill-commons
  side ships, so today those methods are dead code from the runtime's perspective. They
  exist so the coordinated release in `killbill-commons` is a one-line annotation + caller
  change rather than a re-design.
- The new `slow` tests compile cleanly but could not be executed in this environment;
  they are written against the same Mockito-bound `BillingInternalApi` pattern as the
  long-standing `TestInvoiceDispatcher#testIllegalInvoicing`, so they should behave
  consistently with that proven baseline.
