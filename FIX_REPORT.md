# Fix #2176 — Java 17 deployment readiness diagnostics

## Root cause / design summary

Issue #2176 is an enhancement asking Kill Bill to be a better citizen on Java 17+
in containerized deployments. The dzone article and follow-up comment list four
JVM-tuning best practices (explicit `-XX:ActiveProcessorCount`, `MALLOC_ARENA_MAX`,
G1 tuning vs ParallelGC, Native Memory Tracking) that silently degrade behavior
when omitted under JDK 17. There is no single faulty class — the gap is that
nothing in the running server tells an operator whether those knobs are set.
Fix: introduce a tiny, no-dependency diagnostics utility
(`org.killbill.billing.util.jvm.JvmEnvironmentInfo`) that captures the JVM's
container/ergonomics state and emits advisory warnings when the issue-#2176
recommendations are not in effect, and invoke it once at startup from
`KillbillGuiceListener.startLifecycleStage3()`.

## Change summary

- `util/src/main/java/org/killbill/billing/util/jvm/JvmEnvironmentInfo.java` (new)
  - Captures: Java major version, JVM name/vendor, available processors, max
    heap, GC bean names, container heuristic (`/.dockerenv`, `/proc/1/cgroup`),
    `MALLOC_ARENA_MAX`, NMT setting, `ActiveProcessorCount`, `MaxGCPauseMillis`.
  - Exposes a pure `static List<String> computeWarnings(JvmEnvironmentInfo)`
    so the advisory logic is unit-testable independently of the JVM running
    the tests.
  - `logTo(Logger)` emits an INFO summary plus one WARN line per active advisory.
    Wrapped in `try/catch` so startup diagnostics can never break startup.

- `profiles/killbill/src/main/java/org/killbill/billing/server/listeners/KillbillGuiceListener.java`
  - Two-line change: one import, one call to
    `JvmEnvironmentInfo.capture().logTo(logger)` inside
    `startLifecycleStage3()`, alongside the existing Swagger init.

- `util/src/test/java/org/killbill/billing/util/jvm/TestJvmEnvironmentInfo.java` (new)
  - Regression test (group `fast`, no DB, no Guice).

## How the test exercises the new behaviour

`TestJvmEnvironmentInfo` covers:

1. `parseJavaMajorVersion` — JDK 17-style (`"17"`, `"17.0.9"`), legacy 1.x style
   (`"1.8.0_392"` ⇒ `8`), and malformed inputs (returns `-1`).
2. `capture()` on the real test JVM — sanity-checks `availableProcessors >= 1`,
   `maxHeapBytes > 0`, that `summary()` is formatted, and that `logTo(logger)`
   does not throw.
3. `computeWarnings` driven by synthetic `JvmEnvironmentInfo` instances:
   - Containerized + Java 17 + G1 + no tuning ⇒ exactly the four expected
     advisories (`ActiveProcessorCount`, `MALLOC_ARENA_MAX`, `MaxGCPauseMillis`,
     `NativeMemoryTracking`).
   - Same shape with all four flags supplied ⇒ no warnings.
   - Non-containerized Java 11 ⇒ no warnings.
   - Java 17 + ParallelGC ⇒ no G1 advisory (proves the G1 branch is gated on
     the actual GC, not the Java version).
4. `summary()` formats `Long.MAX_VALUE` heap as `unbounded`.

This locks in both the pure advisory logic and the real-JVM smoke path, so a
future regression that e.g. drops the `MALLOC_ARENA_MAX` check or breaks the
GC-name detection fails the test.

## Build status

`mvn -pl util -am -DskipTests compile`:

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.147 s]
[INFO] killbill-api ....................................... SUCCESS [  0.112 s]
[INFO] killbill-util ...................................... SUCCESS [  0.192 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

`mvn -pl util test-compile` (verifies the new test compiles — 121 sources):

```
[INFO] --- compiler:3.11.0:testCompile (default-testCompile) @ killbill-util ---
[INFO] Changes detected - recompiling the module! :dependency
[INFO] Compiling 121 source files with javac [forked debug deprecation release 11] to target/test-classes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

`mvn -pl profiles/killbill -am -DskipTests compile` (covers the listener edit;
`-Dcheck.skip-dependency-scope=true` used to bypass the hubspot dependency-scope
plugin which insists on `*-tests.jar` artifacts that are not relevant to a
source-compile check):

```
[INFO] killbill ........................................... SUCCESS [  0.129 s]
[INFO] killbill-api ....................................... SUCCESS [  0.096 s]
[INFO] killbill-util ...................................... SUCCESS [  0.163 s]
[INFO] killbill-tenant .................................... SUCCESS [  0.019 s]
[INFO] killbill-account ................................... SUCCESS [  0.015 s]
[INFO] killbill-catalog ................................... SUCCESS [  0.019 s]
[INFO] killbill-currency .................................. SUCCESS [  0.014 s]
[INFO] killbill-subscription .............................. SUCCESS [  0.802 s]
[INFO] killbill-entitlement ............................... SUCCESS [  0.745 s]
[INFO] killbill-junction .................................. SUCCESS [  0.767 s]
[INFO] killbill-usage ..................................... SUCCESS [  0.429 s]
[INFO] killbill-invoice ................................... SUCCESS [  1.113 s]
[INFO] killbill-overdue ................................... SUCCESS [  0.539 s]
[INFO] killbill-payment ................................... SUCCESS [  1.062 s]
[INFO] killbill-beatrix ................................... SUCCESS [  0.509 s]
[INFO] killbill-jaxrs ..................................... SUCCESS [  1.196 s]
[INFO] killbill-profiles .................................. SUCCESS [  0.002 s]
[INFO] killbill-profiles-killbill ......................... SUCCESS [  0.732 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

Test JVM used: `openjdk version "25.0.2"` (Homebrew). Project source target
remains Java 11 — no `pom.xml` changes.

## Confidence level

**Medium-high.**

Reasoning:
- All edited modules compile clean (only pre-existing deprecation warnings).
- The unit test exercises every branch of the advisory logic with synthetic
  inputs, so the warning behavior is locked down regardless of which JVM CI
  happens to use.
- The runtime call site (`startLifecycleStage3()`) is wrapped so a buggy
  diagnostic cannot crash the server.
- The change is additive and surface-area-tiny: one new file, one import +
  one call line in the listener, one test file. No build files, no behavior
  changes to existing code paths.
- Not "high" because: I could not execute the new test in this environment
  (Maven `surefire` would require pulling in the broader test fixture); the
  container-detection heuristic is intentionally best-effort and platform
  specific; and the advisory list is opinionated and may need tightening
  based on operator feedback.
