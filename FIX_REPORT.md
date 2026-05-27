# Fix report — issue #2224: BCD_UPDATE event is not returned from subscription events

## Root cause / design summary

When the entitlement layer translates lower-level subscription-base transitions
into the public `SubscriptionEvent` list returned by the API, it routes each
`SubscriptionBaseTransitionType` through
`SubscriptionEventOrdering.toEventTypes` (in
`entitlement/src/main/java/org/killbill/billing/entitlement/api/SubscriptionEventOrdering.java`).
That `switch` handles `CREATE`/`TRANSFER`, `CHANGE`, `CANCEL`/`EXPIRED`, and
`PHASE`, but `BCD_CHANGE` (produced by `SubscriptionBaseTransitionData.toSubscriptionTransitionType`
when the underlying event is of type `BCD_UPDATE`) falls through to the default
branch and is mapped to `Collections.emptyList()`. The transition is therefore
silently dropped before reaching `DefaultSubscriptionBundleTimeline` /
`SubscriptionJson`, which is why the BCD update is absent from the `events`
field of the REST response (issue #2224). The transition itself is correctly
created in `DefaultSubscriptionBase.rebuildTransitionsInternal` — the bug is
purely in the entitlement-level translation.

The omission appears to be unintentional: there is no semantic reason to hide
the event, and the public `SubscriptionEventType` enum (`killbill-api`) does not
expose a dedicated `BCD_CHANGE` value, so the closest matching event type that
the API can express is `CHANGE`. The fix maps `BCD_CHANGE` → `CHANGE`.

## Change summary

- `entitlement/src/main/java/org/killbill/billing/entitlement/api/SubscriptionEventOrdering.java`
  — added a `case BCD_CHANGE:` that falls through to the existing `CHANGE`
  arm, returning `List.of(SubscriptionEventType.CHANGE)`. A short comment
  documents why BCD updates surface as `CHANGE` (no dedicated enum value in
  killbill-api 0.54.0).
- `entitlement/src/test/java/org/killbill/billing/entitlement/api/TestDefaultSubscriptionBundleTimeline.java`
  — added `testBCDUpdateEventIsIncludedInTimeline`, a fast unit test that
  builds an entitlement timeline containing CREATE → PHASE → BCD_UPDATE
  base transitions, then asserts the resulting `SubscriptionEvent` list
  contains four events (`START_ENTITLEMENT`, `START_BILLING`, `PHASE`,
  `CHANGE`) and that the trailing `CHANGE` event carries the id and
  effective date of the BCD transition.

## How the test exercises the fix / new behaviour

The test mocks an entitlement whose `SubscriptionBase` returns a list of
transitions including a `BCD_UPDATE` event (which maps to a `BCD_CHANGE`
transition). It then constructs a `DefaultSubscriptionBundleTimeline` (the same
code path used by the JAXRS resource when building the response of the
`retrieve-a-subscription-by-id` endpoint) and inspects `getSubscriptionEvents()`.

Before the fix the `BCD_CHANGE` transition was dropped and the test would see
only 3 events. After the fix the BCD update appears as a `CHANGE`-typed
`SubscriptionEvent` with the same id and effective date as the underlying
`BCD_UPDATE` transition, mirroring how the field is rendered in the REST
response by `SubscriptionJson.EventSubscriptionJson`.

## Build status

`mvn -pl entitlement -am -DskipTests compile`:

```
[INFO] killbill ........................................... SUCCESS [  0.204 s]
[INFO] killbill-api ....................................... SUCCESS [  0.734 s]
[INFO] killbill-util ...................................... SUCCESS [  1.531 s]
[INFO] killbill-tenant .................................... SUCCESS [  0.676 s]
[INFO] killbill-account ................................... SUCCESS [  0.691 s]
[INFO] killbill-catalog ................................... SUCCESS [  0.920 s]
[INFO] killbill-subscription .............................. SUCCESS [  0.930 s]
[INFO] killbill-entitlement ............................... SUCCESS [  0.903 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

Test classes also compile cleanly (`mvn -pl entitlement -am -DskipTests test-compile`
→ BUILD SUCCESS). The test itself cannot be executed in this environment because
Mockito's inline mock maker is incompatible with the JDK 25 runtime present on
the machine ("Mockito cannot mock this class: class org.killbill.clock.ClockMock"),
which is an environment limitation analogous to the embedded-MySQL ARM issue
noted in the task description. The compile-clean bar required by the task is
met.

## Confidence level

**Medium-high.**

- The root cause is mechanically obvious: a missing `case` in a `switch`
  whose other arms exhibit exactly the same shape; the BCD transition itself
  is already constructed and surfaced by the subscription module, only the
  entitlement-level translation drops it.
- The mapping target (`SubscriptionEventType.CHANGE`) is the only option that
  keeps the change limited to this repo without modifying the external
  `killbill-api` enum. There is one residual semantic ambiguity: consumers
  cannot now distinguish a plan-CHANGE event from a BCD-CHANGE event purely
  from the `eventType` field. Most callers can still disambiguate by
  observing that for a BCD update `prevPlan == nextPlan` (and likewise for
  phase/priceList), since `DefaultSubscriptionBase.rebuildTransitionsInternal`
  carries the existing plan/phase through unchanged.
- The `DefaultSubscriptionEvent.overlaps`-based deduplication in
  `SubscriptionEventOrdering.removeOverlappingSubscriptionEvents` was
  inspected: a BCD event mapped to `CHANGE` will not overlap a preceding
  plan-`CHANGE` event because their `prevPlan` values differ, and it will
  not overlap a preceding `PHASE` event because the `eventType` field
  differs. No regression in deduplication is expected.
- Confidence is not "high" only because the test cannot be executed in this
  environment (JDK 25 / Mockito incompatibility) and because a full
  integration run against the JAXRS layer would be the strongest validation.
