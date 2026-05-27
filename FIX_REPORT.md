# Fix Report — Kill Bill issue #2207

## Root cause / design summary

In `invoice/src/main/java/org/killbill/billing/invoice/InvoiceDispatcher.java`, two
methods participate in the billing-date notification-queue lock-failure path:
`processAccount(...)` (the catch-`LockFailedException` site) and the private helper
`rescheduleProcessAccount(UUID, InternalCallContext)`. The path had two operational
gaps relative to the bus-event paths that throw `QueueRetryException`:

1. `rescheduleProcessAccount` logged at `INFO` without the `accountId`, so a successful
   one-shot reschedule was indistinguishable across accounts in the log stream.
2. When `getRescheduleIntervalOnLock` was not configured, `rescheduleProcessAccount`
   returned `false` and the caller only emitted a `WARN`. No further notification was
   ever enqueued, silently abandoning the account.

The asymmetry of the *mechanism* (notification reschedule vs. `QueueRetryException`)
is intentional — see the proposal in the issue — so the fix tightens the
notification-queue path without unifying it with the bus-event path.

## Change summary

- `invoice/src/main/java/org/killbill/billing/invoice/InvoiceDispatcher.java`
  - `rescheduleProcessAccount(...)`: added the `accountId` and `nextRescheduleDt` to
    the success log (`"Failed to acquire lock, rescheduling invoice for accountId='{}' at '{}'"`),
    and replaced the prior single-line "we only look at the first value" comment with
    a longer explanation of *why* this path does single-reschedule (no attempt count
    can be tracked across notification reschedules, unlike the
    `RetryableService`-backed `QueueRetryException` path).
  - `processAccount(...)` — `catch (LockFailedException)` branch: when
    `rescheduleProcessAccount(...)` returns `false` (empty schedule), the warn message
    now mentions parking, and the account is parked via the existing `parkAccount(...)`
    helper so the failure is surfaced (matching the proposed implementation #3 in the
    issue).

- `invoice/src/test/java/org/killbill/billing/invoice/TestInvoiceDispatcherLockHandling.java`
  *(new)* — three `groups = "fast"` unit tests, pure Mockito, no embedded DB.

## How the test exercises the fix / new behaviour

`TestInvoiceDispatcherLockHandling` builds an `InvoiceDispatcher` with a mocked
`GlobalLocker` whose `lockWithNumberOfTries(...)` always throws `LockFailedException`,
a mocked `InvoiceConfig`, a real `ParkedAccountsManager` backed by a mocked
`TagInternalApi`, a mocked `InvoiceDao`, and a fixed-`Clock`:

- `testLockFailureWithRescheduleConfiguredCallsRescheduleAndDoesNotPark` — with a
  non-empty `getRescheduleIntervalOnLock` schedule (single `5m` period), it asserts
  that `invoiceDao.rescheduleInvoiceNotification(...)` is called exactly once with
  `accountId` and `clock + 5m`, and that `tagApi.addTag(PARK_TAG_DEFINITION_ID, ...)`
  is **never** called.
- `testLockFailureWithEmptyRescheduleParksTheAccount` — with an empty schedule it
  asserts the inverse: no reschedule, and `tagApi.addTag(accountId, ACCOUNT,
  PARK_TAG_DEFINITION_ID, ctx)` is called exactly once. This is the regression
  test for the new behaviour described in issue #2207 §3 ("Park when lock
  rescheduling is not configured").
- `testLockFailureOnApiCallStillThrowsInvoiceApiException` — with `isApiCall=true`,
  the dispatcher must rethrow as `InvoiceApiException` and must not reschedule
  or park; this guards the unchanged API semantics from regressing.

Captured log line during the test run confirms the new format:

```
INFO  o.k.b.i.InvoiceDispatcher - Failed to acquire lock, rescheduling invoice for
  accountId='456e1be1-0b6f-4488-9bea-6dfb736ce00e' at '2026-01-01T00:05:00.000Z'
```

## Build status

`mvn -pl invoice -am -DskipTests compile`:

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.216 s]
[INFO] killbill-api ....................................... SUCCESS [  0.203 s]
[INFO] killbill-util ...................................... SUCCESS [  0.429 s]
[INFO] killbill-tenant .................................... SUCCESS [  0.205 s]
[INFO] killbill-account ................................... SUCCESS [  0.215 s]
[INFO] killbill-catalog ................................... SUCCESS [  0.181 s]
[INFO] killbill-subscription .............................. SUCCESS [  0.183 s]
[INFO] killbill-entitlement ............................... SUCCESS [  0.177 s]
[INFO] killbill-junction .................................. SUCCESS [  0.174 s]
[INFO] killbill-usage ..................................... SUCCESS [  0.162 s]
[INFO] killbill-invoice ................................... SUCCESS [  0.166 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

`mvn -pl invoice test -Dtest=TestInvoiceDispatcherLockHandling`:

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.563 s
  -- in org.killbill.billing.invoice.TestInvoiceDispatcherLockHandling
[INFO] Results:
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Confidence level

**High.** The change is small and confined to one method body plus one helper signature
log line in `InvoiceDispatcher`. It exactly matches the implementation proposed in the
issue (items 1–4). The new unit test exercises both the "reschedule" and "park" branches
of the catch block as well as the unchanged API-caller behaviour, and runs in the `fast`
group without the embedded MySQL dependency.

The mechanism asymmetry between Path A (notification queue) and Paths B / C (bus events
via `QueueRetryException`) is preserved by design, as confirmed in the issue body.
