# Fix Report — Issue #2224

**BCD_UPDATE event does not seem to be returned from subscription events**

## Root cause / design summary

When the timeline of a subscription is built for the API response, every
`SubscriptionBaseTransition` is converted into a user-facing `SubscriptionEvent`
by `SubscriptionEventOrdering.computeSubscriptionBaseEvents()`
(`entitlement/src/main/java/org/killbill/billing/entitlement/api/SubscriptionEventOrdering.java`).
The mapping happens in the private switch
`SubscriptionEventOrdering.toEventTypes(SubscriptionBaseTransitionType)`.
That switch never handled `SubscriptionBaseTransitionType.BCD_CHANGE`
(the internal type written to `subscription_events` as `BCD_UPDATE` rows),
so BCD updates fell through to `default → Collections.emptyList()` and were
silently dropped from `Subscription.getSubscriptionEvents()` — and therefore
from the JSON returned by `GET /subscriptions/{id}`. BCD updates were already
emitted on the external bus (`ExtBusEventType.SUBSCRIPTION_BCD_CHANGE`) but
never reached the REST events array. This is a bug, not an intentional
omission: there is no business reason to hide BCD changes from clients that
read the subscription timeline.

The public `SubscriptionEventType` enum in `killbill-api` 0.54.0 (locked by
`killbill-oss-parent`) does not have a dedicated BCD value, so the fix maps
`BCD_CHANGE` to `SERVICE_STATE_CHANGE` — the closest existing value — and
tags the produced event with `serviceStateName = "BCD_CHANGE"` and
`serviceName = "billing-service"` so consumers can identify it unambiguously.

## Change summary

| File | Change |
| ---- | ------ |
| `entitlement/src/main/java/org/killbill/billing/entitlement/api/SubscriptionEventOrdering.java` | Added `case BCD_CHANGE → SERVICE_STATE_CHANGE` to `toEventTypes`. Extended `toSubscriptionEvent` to set `serviceName = BILLING_SERVICE_NAME` and `serviceStateName = "BCD_CHANGE"` when the transition originates from a `BCD_CHANGE`, so the event is distinguishable from other `SERVICE_STATE_CHANGE` events. |
| `jaxrs/src/main/java/org/killbill/billing/jaxrs/json/SubscriptionJson.java` | Patched `EventSubscriptionJson.getAuditLogsForSubscriptionEvent` to route audit-log lookups for `serviceStateName = "BCD_CHANGE"` events to `getAuditLogsForSubscriptionEvent` (rows live in `subscription_events`) instead of the default `BLOCKING_STATES` path that the enum's `objectType` would otherwise dictate. Added the corresponding import of `SubscriptionBaseTransitionType`. |
| `entitlement/src/test/java/org/killbill/billing/entitlement/api/TestSubscriptionEventOrderingBCD.java` | New regression test class with two tests (see below). |

No build files, schema, migrations, or unrelated code were touched.

## How the test exercises the fix / new behaviour

`TestSubscriptionEventOrderingBCD` (entitlement module, `fast` group, no DB):

1. **`testBcdChangeTransitionSurfacedAsSubscriptionEvent`** — mocks a
   `SubscriptionBaseTransition` whose `getTransitionType()` returns
   `BCD_CHANGE`, invokes the package-visible
   `SubscriptionEventOrdering.toSubscriptionEvent(...)`, and asserts that the
   produced `SubscriptionEvent`:
   - has `SubscriptionEventType == SERVICE_STATE_CHANGE`,
   - has `serviceStateName == "BCD_CHANGE"`,
   - has `serviceName == EntitlementOrderingBase.BILLING_SERVICE_NAME`,
   - preserves the original `id`, `entitlementId`, and `effectiveDate`.

   Before the fix `toEventTypes(BCD_CHANGE)` returned an empty list, so the
   transition never reached `toSubscriptionEvent` at all and the event was
   missing from the timeline.

2. **`testNonBcdTransitionUsesDefaultServiceStateName`** — pins the existing
   behaviour for non-BCD transitions (here `CHANGE`): `serviceStateName`
   must remain `eventType.toString()` so the BCD special-case does not leak
   into other event types.

Both tests pass:

```
[INFO] Running org.killbill.billing.entitlement.api.TestSubscriptionEventOrderingBCD
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.428 s
[INFO] BUILD SUCCESS
```

## Build status

`mvn -pl entitlement,jaxrs -am -DskipTests compile` (clean build):

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.153 s]
[INFO] killbill-api ....................................... SUCCESS [  0.631 s]
[INFO] killbill-util ...................................... SUCCESS [  1.377 s]
[INFO] killbill-tenant .................................... SUCCESS [  0.657 s]
[INFO] killbill-account ................................... SUCCESS [  0.721 s]
[INFO] killbill-catalog ................................... SUCCESS [  0.940 s]
[INFO] killbill-subscription .............................. SUCCESS [  0.926 s]
[INFO] killbill-entitlement ............................... SUCCESS [  0.879 s]
[INFO] killbill-overdue ................................... SUCCESS [  0.673 s]
[INFO] killbill-jaxrs ..................................... SUCCESS [  1.354 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.458 s
```

`mvn -pl entitlement -am test-compile` (verifies the regression test
compiles):

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
...
[INFO] killbill-entitlement ............................... SUCCESS [  1.006 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  6.103 s
```

## Confidence level

**Medium–high.**

- The root cause is mechanical and pinpoint: a missing `case` in a small
  `switch`. The fix is symmetrical with the surrounding cases and behind
  no feature flag.
- The chosen `SubscriptionEventType.SERVICE_STATE_CHANGE` mapping is the
  only API-compatible choice given the locked `killbill-api` 0.54.0 enum
  (no `BCD_CHANGE` value). A future API bump should add a dedicated
  `BCD_CHANGE` value and the mapping can move to that.
- The audit-log routing patch in `EventSubscriptionJson` is required
  because `SERVICE_STATE_CHANGE.getObjectType() == BLOCKING_STATES`, but
  BCD rows live in `subscription_events`. Without the patch BCD events
  would appear but their audit logs would silently be empty.
- The new unit tests cover both the positive case (BCD surfaced and tagged
  correctly) and a guardrail against accidentally regressing the existing
  behaviour for non-BCD transitions.
- Confidence is not "high" because I could not run the broader integration
  suite (`TestWithBCDUpdate.java`) — those tests require an embedded MySQL
  ARM64 binary that is unavailable in this environment, as noted in the
  task constraints. The compilation and the targeted unit test are clean.
