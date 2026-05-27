# Issue #2176 — Upgrade to Java 17: container-aware JVM diagnostics

## Root cause / design summary

Issue #2176 asks Kill Bill to be ready for JDK 17+ in container deployments.
The referenced article emphasises that the practical risks of the JDK 8 → 17
move are runtime / native-memory behaviours rather than source-level changes:
container CPU detection bugs, glibc malloc arena explosion, G1 native-memory
overhead, and the loss of JDK 8 monitoring heuristics. Kill Bill had no boot
output describing the JVM it was running on, so operators had no easy way to
verify that critical container-tuning flags (`-XX:ActiveProcessorCount`,
`MALLOC_ARENA_MAX`, `-XX:NativeMemoryTracking`, `-XX:+UseContainerSupport`,
matched `-Xms`/`-Xmx`) were actually in effect. The boot path that already
records node identity — `org.killbill.billing.util.nodes.DefaultKillbillNodesService#init` —
is the natural place to surface this diagnostic.

## Change summary

- **`util/src/main/java/org/killbill/billing/util/nodes/JvmInfo.java`** (new) —
  pure utility that captures a JVM snapshot via `RuntimeMXBean`,
  `MemoryMXBean`, `GarbageCollectorMXBean`, system properties and the
  environment. Exposes Java version/vendor/VM, `availableProcessors()`,
  heap init/max, GC names, `-XX:ActiveProcessorCount`, `MALLOC_ARENA_MAX`,
  whether NMT is enabled and whether `UseContainerSupport` is on. Computes
  a list of WARN-level recommendations based on the article's checklist
  (active processor count, malloc arenas, native memory tracking,
  container support, `-Xms`/`-Xmx` equality). `captureAndLog(Logger)`
  emits one INFO summary line followed by one WARN per missing
  recommendation. A package-private `build(...)` overload accepts injected
  inputs so the warning logic is unit-testable without manipulating real
  JVM args or env vars.
- **`util/src/main/java/org/killbill/billing/util/nodes/DefaultKillbillNodesService.java`** —
  call `JvmInfo.captureAndLog(logger)` at the top of the
  `@LifecycleHandlerType(BOOT) init()` method so diagnostics appear once
  per node start, alongside the existing node-info bootstrap.
- **`util/src/test/java/org/killbill/billing/util/nodes/TestJvmInfo.java`** (new) —
  TestNG `fast` group; covers `capture()` against the real JVM and the
  warning logic against synthetic inputs.

No build files or other modules were touched.

## How the test exercises the new behaviour

`TestJvmInfo` has five `fast`-group tests:

1. `testCaptureReturnsRuntimeFacts` — calls the production `capture()` and
   asserts non-empty `javaVersion`/`javaVmName`, `availableProcessors >= 1`,
   and at least one GC MXBean. This guards the wiring between `JvmInfo`
   and the JDK management APIs.
2. `testWarningsWhenContainerTuningIsMissing` — builds a snapshot with no
   `-XX:ActiveProcessorCount`, no `MALLOC_ARENA_MAX`, no NMT and mismatched
   `-Xms`/`-Xmx`, then asserts the four corresponding warnings are present.
3. `testNoWarningsWhenAllRecommendationsSatisfied` — feeds in the full
   set of recommended flags and asserts the warning list is empty.
4. `testContainerSupportDisabledIsFlagged` — passes
   `-XX:-UseContainerSupport` and asserts both `isUseContainerSupport()`
   becomes `false` and the matching warning fires.
5. `testNativeMemoryTrackingOffIsTreatedAsDisabled` — `-XX:NativeMemoryTracking=off`
   is not counted as enabled, mirroring how the JVM itself treats the value.

All five passed locally:

```
[INFO] Running org.killbill.billing.util.nodes.TestJvmInfo
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.114 s
```

## Build status

```
$ mvn -pl util -am -DskipTests compile
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.215 s]
[INFO] killbill-api ....................................... SUCCESS [  0.757 s]
[INFO] killbill-util ...................................... SUCCESS [  1.560 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.687 s
```

Test compile + run:

```
$ mvn -pl util -am test -Dtest=TestJvmInfo -Dsurefire.failIfNoSpecifiedTests=false
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Confidence level

**Medium-high.** The change is additive, has no external dependencies
beyond `java.lang.management` (present in JDK 11 and 17), is exercised
directly by unit tests, and lives behind the existing
`DefaultKillbillNodesService` boot hook so the integration cost is one
INFO line plus zero-or-more WARN lines per node start. The remaining
uncertainty is policy-shaped, not technical: a different reviewer might
want these diagnostics exposed via the JMX/metrics surface or via the
`NodeInfo` payload rather than only the log, but those would be
follow-on enhancements layered on top of `JvmInfo` rather than
replacements for it.
