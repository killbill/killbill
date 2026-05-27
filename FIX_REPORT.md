# Fix Report — Issue #2156: Adopt UUIDv7 for all identifiers

## Root cause / design summary

The codebase already centralises identifier generation through
`org.killbill.billing.util.UUIDs#randomUUID()`
(`api/src/main/java/org/killbill/billing/util/UUIDs.java`), which is what
~all entity-creation paths call (`DefaultAccount`, `DefaultSubscriptionBaseBundle`,
`PaymentModelDao`, `PaymentTransactionModelDao`, `PaymentAttemptModelDao`,
`InvoiceDispatcher`, `Context`, `EventBaseBuilder`, `TagModelDao`, etc. — 200+
call sites). Previously that helper minted UUIDv4 values via its internal
`rndUUIDv4()` method, so primary-key columns received random bytes that
sit anywhere in a B-tree, defeating insertion-order locality.

Per the issue and pierre's comment, fully replacing `record_id` with
`id` as the storage primary key is a 6-month+ effort. The smallest change
that actually advances the issue's goal — and the one with no migration
risk — is to switch the canonical identifier source to UUIDv7
(RFC 9562: 48-bit unix-ms timestamp || 4-bit version || 12-bit
sub-ms sequence || 2-bit variant || 62 random bits). Every new `id`
created from this point on is time-ordered; PostgreSQL 18+ (and any
DB that accepts UUID(7) primary keys in the future) will benefit
without further code changes. Existing rows are unaffected because
`UUID` columns store the bytes opaquely.

## Change summary by file

- **`api/src/main/java/org/killbill/billing/util/UUIDs.java`**
  - `randomUUID()` now delegates to a new `rndUUIDv7()` private method
    instead of `rndUUIDv4()`.
  - Added `randomUUIDv7()` (explicit) and `randomUUIDv4()` (legacy
    escape hatch) public helpers so call sites can still request a
    fully-random UUID if they have a reason to.
  - `rndUUIDv7()` lays the bits out per RFC 9562 and, under a small
    static monitor, keeps a `(lastMillis, sequence)` pair so that
    successive calls within the same millisecond are still strictly
    monotonically increasing (and never collide even at burst rates
    exceeding the millisecond resolution). The 62 low bits come from
    the existing `ThreadLocal<Random>` used by the v4 generator, so
    entropy quality is unchanged.
  - Class-level Javadoc explains the new default and the v4/v7 split.

- **`util/src/test/java/org/killbill/billing/util/TestUUIDs.java`** (new)
  - Six `@Test(groups = "fast")` cases that lock in the new contract.

## How the test exercises the new behaviour

`TestUUIDs` does not need DI, so it runs as a plain TestNG class:

1. `testRandomUUIDReturnsVersion7` — generates 1 000 ids via
   `UUIDs.randomUUID()` and asserts each carries `version() == 7` and
   IETF variant. Would have failed before the change (v4).
2. `testExplicitV7HelperReturnsVersion7` — covers the new explicit
   `randomUUIDv7()` helper.
3. `testLegacyV4HelperStillReturnsVersion4` — pins the v4 escape
   hatch so future refactors don't quietly drop it.
4. `testEmbeddedTimestampMatchesWallClock` — extracts the top 48
   bits of the id and checks they fall inside `[before, after]`
   wall-clock bounds, proving the timestamp prefix is real
   (a pure-random v4 would fail this).
5. `testMonotonicWithinSameMillisecond` — generates 5 000 ids in a
   tight loop (many land in the same ms) and asserts each is
   strictly `>` the previous one under unsigned 128-bit ordering.
   This is the key property that makes UUIDv7 useful as a B-tree
   primary key.
6. `testUniquenessUnderConcurrency` — 8 threads × 2 000 ids each, all
   added to a `HashSet`; asserts no collisions and all are v7. This
   exercises the shared monotonic counter under contention.

Helper `compareUnsigned()` is needed because `UUID#compareTo` treats
the long halves as signed, which inverts ordering whenever the
timestamp's high bit flips across an ordinary millisecond boundary.

## Build status

`mvn -pl api -am clean compile -DskipTests`:

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.212 s]
[INFO] killbill-api ....................................... SUCCESS [  0.905 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

`mvn -pl util -am test -Dtest=TestUUIDs`:

```
[INFO] Running org.killbill.billing.util.TestUUIDs
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.148 s
[INFO]
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.356 s]
[INFO] killbill-api ....................................... SUCCESS [  0.415 s]
[INFO] killbill-util ...................................... SUCCESS [  1.395 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

(A full-repo `mvn -DskipTests compile` halts on an unrelated, pre-existing
missing-artifact error — `killbill-catalog:jar:tests:0.24.17-SNAPSHOT` —
that is not produced by this change and is consistent with the
environment caveat in the task brief.)

## Confidence level

**High** for the immediate scope (identifier generation now emits
UUIDv7 with the correct bit layout, monotonic ordering, and no
collisions under concurrency — all directly tested) and for backward
compatibility (no existing code reads `UUID.version()` of stored ids;
the `randomUUIDv4()` escape hatch is preserved).

**Medium** as a complete answer to the broader issue: as pierre noted
in the GitHub thread, harvesting the database benefit (using UUID as
the literal primary key column instead of `record_id`) is a much
larger schema migration that this change does not attempt. What this
change does deliver is the prerequisite — every new `id` minted from
now on is a v7 — so that the future schema migration becomes a pure
DDL change rather than also requiring an id-rewrite.
