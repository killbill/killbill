# Fix for Kill Bill issue #2156 — Adopt UUIDv7 for all identifiers

## Root cause / design summary

Kill Bill mints every domain-object identifier through a single helper:
`UUIDs.randomUUID()` at `api/src/main/java/org/killbill/billing/util/UUIDs.java`.
That helper delegates to the private `rndUUIDv4()` implementation, which
produces a purely random UUIDv4 — fine for uniqueness but poor for B-tree
primary-key locality on a write-heavy workload, which is the concern raised
in the issue. Pierre's comment notes that fully switching from `record_id`
to `id` is a multi-quarter effort, so the change here is scoped to giving
Kill Bill the ability to generate time-ordered UUIDv7 identifiers via the
same `UUIDs.randomUUID()` entry point, gated by an opt-in system property
so existing deployments keep their current v4 behavior.

## Change summary

### `api/src/main/java/org/killbill/billing/util/UUIDs.java`
- Added `randomUUIDv7()` which builds a UUIDv7 per
  [RFC 9562 §5.7](https://www.rfc-editor.org/rfc/rfc9562#name-uuid-version-7):
  48 bits of Unix milliseconds, 4 bits version (`0x7`), 12 bits `rand_a`,
  2 bits IETF variant, 62 bits `rand_b`. Entropy is sourced from the
  existing `threadRandom` (the lightweight SHA1PRNG-backed
  `LightSecureRandom`), so we reuse the same RNG path the existing v4
  generator relies on.
- Added `randomUUIDv4()` as the public name for the historical generator;
  the existing private `rndUUIDv4()` is kept as-is and now serves both
  entry points.
- Made `randomUUID()` dispatch to v4 (default) or v7 (when the
  `org.killbill.uuid.v7.enabled` system property is set to `true`).
  Every existing call site (135 occurrences across 78 files) is unchanged
  and automatically benefits when v7 is turned on — no source churn
  required.
- Added a process-wide monotonic millisecond counter
  (`nextMonotonicMillis()`, backed by an `AtomicLong`) so concurrent
  v7 generation in the same wall-clock millisecond — or small backwards
  clock jumps from NTP — still yield strictly increasing identifiers.
  Without this guard, UUIDv7 ids generated in the same ms could shuffle
  randomly on the `rand_a`/`rand_b` bytes, defeating the whole point of a
  sortable id.
- Exposed two small helpers: `isUUIDv7Enabled()` (so other code can branch
  on the toggle without reading the system property itself) and
  `timestampMillisFromV7(UUID)` (useful for migration tooling, log
  correlation, and the new tests). The latter validates the UUID version
  and throws `IllegalArgumentException` for non-v7 input.

### `util/src/test/java/org/killbill/billing/util/TestUUIDs.java` (new)
A TestNG `fast`-group suite covering the new behavior. See below.

## How the test exercises the fix / new behaviour

`TestUUIDs` has 7 test methods, all in the `fast` group:

1. `testRandomUUIDv4VersionAndVariant` — explicit v4 path still returns
   `version()==4`, `variant()==2` (IETF). Guards against any regression to
   the historical generator.
2. `testRandomUUIDv7VersionAndVariant` — `randomUUIDv7()` returns
   `version()==7`, `variant()==2`. Confirms the bit layout is right.
3. `testRandomUUIDv7EncodesTimestampDeterministically` — calls the
   package-private `rndUUIDv7(unixMillis)` with a fixed timestamp and
   asserts `timestampMillisFromV7(id) == unixMillis`. This is independent
   of the JVM-wide monotonic counter, so it stays deterministic regardless
   of which other tests have run first.
4. `testRandomUUIDv7IsMonotonic` — generates 10,000 v7 ids back-to-back
   and asserts strict `compareTo > 0` ordering on every step. This is the
   key property the issue is about: time-ordered ids that play well with
   B-tree indexes. It only passes because of the monotonic-millis guard;
   without it, the random `rand_a` bytes would reorder pairs of ids
   generated within the same millisecond.
5. `testRandomUUIDv7IsUnique` — 10,000 ids, all distinct.
6. `testTimestampExtractionRejectsNonV7` — `timestampMillisFromV7()`
   throws `IllegalArgumentException` when handed a non-v7 UUID.
7. `testRandomUUIDDefaultsToV4` — confirms `isUUIDv7Enabled()` is `false`
   in a JVM that has not set the property, and that `randomUUID()` still
   returns v4 in that mode. This pins the backward-compatibility contract.

Tests live in the `util` module because that's where the project already
keeps `UUIDs`-adjacent unit coverage (`util/src/test/java/.../util/`); the
`api` module has no test sources at all.

## Build status

`mvn -pl api -am -DskipTests compile` (the primary module — `UUIDs.java`
lives in `api/`):

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.219 s]
[INFO] killbill-api ....................................... SUCCESS [  0.796 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

`mvn -pl util -am -Dtest=TestUUIDs -Dsurefire.failIfNoSpecifiedTests=false test`
(executes the new test):

```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.147 s -- in org.killbill.billing.util.TestUUIDs
[INFO] Results:
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.371 s]
[INFO] killbill-api ....................................... SUCCESS [  0.449 s]
[INFO] killbill-util ...................................... SUCCESS [  2.503 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

Note: a full repo-root `mvn -DskipTests compile` fails further downstream
in the `subscription` module because `killbill-catalog:jar:tests` is not
present in the local Maven cache, and a separate `mvn install` against
this checkout fails because SpotBugs 4.7.2.1 cannot parse class files
emitted by the JDK 25 currently on this machine ("Unsupported class file
major version 69"). Both are pre-existing environment issues unrelated to
this change.

## Confidence level

**High.**

- The change is additive and gated behind an opt-in system property; the
  default behavior of `randomUUID()` is bit-for-bit unchanged, so the 78
  call sites that depend on it cannot regress without flipping the flag.
- The v7 layout is mechanically verified by an explicit-timestamp test
  that round-trips an arbitrary 48-bit value through
  `rndUUIDv7`/`timestampMillisFromV7`, plus version/variant assertions.
- The monotonicity guard is exercised at 10k iterations — large enough to
  reliably catch any same-millisecond reordering caused by the random
  `rand_a`/`rand_b` bits.
- Compile and the new test suite both pass cleanly on the targeted
  modules. The pre-existing environment failures (catalog test-jar,
  SpotBugs/JDK 25) do not touch `UUIDs` or any code path I modified.

The one limitation worth flagging: as Pierre's comment notes, real
adoption of UUIDv7 as a database primary key would also need Kill Bill to
stop relying on `record_id` and use `id` directly — that is the
multi-quarter effort described in the issue, and is explicitly out of
scope here. This change unblocks that future work by making sure every
new identifier generated by Kill Bill can already be v7 with a single
JVM flag flip.
