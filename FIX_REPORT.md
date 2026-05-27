# Fix Report — issue #2156: Adopt UUIDv7 for system identifiers

## Root cause / design summary

Issue #2156 asks Kill Bill to start emitting **RFC 9562 UUIDv7** identifiers
instead of the random RFC 4122 v4 values produced today, so that primary keys
become time-ordered (better B-tree locality / write performance) without
breaking the existing `BINARY(16)` / `CHAR(36)` storage shape. Pierre's comment
on the issue makes clear that *replacing* `record_id` with `id` as the actual
primary key column is a 6+ month effort, so the in-scope change is the
generator itself: every Kill Bill subsystem already routes through the
centralised utility `org.killbill.billing.util.UUIDs` (`api/src/main/java/org/killbill/billing/util/UUIDs.java`),
whose static method `UUIDs.randomUUID()` was hard-coded to call a private
`rndUUIDv4()` helper. Switching that single entry point to generate v7 IDs
flows through the rest of the codebase automatically.

## Change summary

### `api/src/main/java/org/killbill/billing/util/UUIDs.java`
- `randomUUID()` now delegates to the new `rndUUIDv7()` generator (was `rndUUIDv4()`).
- Added `randomUUIDv7()` as a self-documenting alias and `randomUUIDv4()` for the
  small set of call sites that may explicitly need a non-time-ordered identifier.
- Added `unixTimestampMillis(UUID)` helper that extracts the embedded 48-bit
  Unix-epoch-ms field from a v7 UUID, throwing `IllegalArgumentException` if the
  supplied UUID is not v7.
- Implemented `rndUUIDv7()` per RFC 9562 §5.7:
  - bits  0–47 (top of MSB): `unix_ts_ms`
  - bits 48–51:               version `0b0111`
  - bits 52–63:               `rand_a`
  - bits 64–65 (top of LSB): variant `0b10`
  - bits 66–127:              `rand_b`
- Uses a per-thread `long[3]` state holding the last emitted `(ts, msb, lsb)`.
  On a same-millisecond call we apply the "monotonic random" method (RFC 9562
  §6.2 method 1): increment the 74-bit random payload by one (with carry from
  `rand_b` into `rand_a` and, on the astronomically unlikely full wrap, a +1ms
  bump of the timestamp). This guarantees that successive UUIDs emitted by the
  same thread are *strictly* increasing under the natural unsigned 128-bit
  byte ordering — the exact property a database B-tree sees.
- Reuses the existing `threadRandom` `ThreadLocal<Random>` (so the existing
  `UUIDs.setRandom(...)` test hook still works) and the bundled
  `LightSecureRandom` PRNG — no new entropy source, no new dependency.

### `util/src/test/java/org/killbill/billing/util/TestUUIDs.java` (new)
A plain-TestNG (`fast` group) regression test, deliberately *not* extending
`UtilTestSuiteNoDB` so it requires no Guice / no embedded bus. Seven test cases:

| Test | What it asserts |
| --- | --- |
| `testRandomUUIDIsVersion7` | `UUIDs.randomUUID().version() == 7` and variant is IETF (the central behavioural switch) |
| `testRandomUUIDv7Explicit` | Same, via the explicit `randomUUIDv7()` alias |
| `testRandomUUIDv4StillAvailable` | `randomUUIDv4()` still yields a true v4 UUID for callers that opt out |
| `testV7TimestampEncodesCurrentMillis` | `unixTimestampMillis(id)` falls between `System.currentTimeMillis()` samples taken just before and after generation — i.e. the timestamp prefix is real wall-clock |
| `testUnixTimestampRejectsNonV7` | The extractor throws `IllegalArgumentException` on a v4 input |
| `testV7MonotonicallyOrderedWithinSameThread` | 10 000 sequential UUIDs are strictly increasing under unsigned 128-bit comparison — proves the B-tree-locality property the issue is asking for |
| `testV7Uniqueness` | 50 000 sequential UUIDs are all distinct |

## How the test exercises the fix / new behaviour

`testRandomUUIDIsVersion7` is the direct regression check: prior to this commit
it would fail with `expected [7] but found [4]`, exactly as it does when run
against the unmodified `UUIDs.java`. `testV7MonotonicallyOrderedWithinSameThread`
is the substantive behavioural guarantee the issue is asking for — random
UUIDv4s would fail this test essentially every run, whereas the new monotonic
v7 implementation must pass it deterministically. `testV7TimestampEncodesCurrentMillis`
proves the timestamp is real (not just any 48-bit prefix), and the v4 escape
hatch test (`testRandomUUIDv4StillAvailable`) protects the small surface area
where a non-time-ordered ID may still be desirable.

## Build status

`mvn -pl util -am -DskipTests compile` (the required verification command):

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.154 s]
[INFO] killbill-api ....................................... SUCCESS [  0.168 s]
[INFO] killbill-util ...................................... SUCCESS [  0.344 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

For additional confidence I also ran the new test directly:

```
mvn -pl util -Dtest=TestUUIDs -DfailIfNoTests=false -Dcheck.skip-spotbugs=true test
…
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.122 s -- in org.killbill.billing.util.TestUUIDs
…
[INFO] BUILD SUCCESS
```

(`-Dcheck.skip-spotbugs=true` is purely an environment workaround — the
SpotBugs version pinned by the project bundles an ASM that can't parse the
Java 25 class files in this sandbox's JDK. It is **not** required for the
compile step the task asks for.)

## Confidence level

**High.**

- Behavioural change is confined to a single method body in a single class that
  is already the codebase's *only* UUID factory; ripple effects are by
  construction — every existing call site keeps the same `UUID` return type and
  the same 128-bit shape that flows into the schema.
- The bit layout matches RFC 9562 §5.7 verbatim and is asserted both
  structurally (`version() == 7`, `variant() == 2`) and semantically
  (timestamp prefix matches wall-clock).
- Monotonicity is proven by a 10 000-iteration loop in the test, not just
  argued; uniqueness over 50 000 iterations rules out trivial off-by-one bugs
  in the same-ms carry logic.
- No build files were touched; no new dependencies; the legacy v4 helper is
  preserved verbatim for any caller that specifically needs random IDs.
- The existing `UUIDs.setRandom(...)` test injection point is untouched and
  flows through the new v7 generator, so downstream tests that seed
  determinism keep working.

The one thing this PR explicitly does *not* do is the larger schema change
Pierre flagged (dropping `record_id` and using `id` as the actual DB primary
key). That remains tracked under #2156 as a follow-up.
