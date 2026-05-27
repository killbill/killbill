# Fix Report — Issue #2176 (Upgrade to Java 17 / JVM-in-container best practices)

## Root cause / design summary

Issue #2176 is an enhancement, not a bug: it tracks the move to JDK 17 and
flags a set of container-related JVM footguns (cgroup-blind CPU detection,
glibc malloc-arena bloat, GC overhead, observability of off-heap growth). Most
of those items are deployment-time JVM flags rather than code, but the one
piece that genuinely belongs in the application is a startup self-check that
makes the situation visible. Today the server boots without ever logging which
JVM, GC, heap, or cgroup view it is running under — when a container is
OOM-killed by the kernel there is nothing in the application logs to point at
`-XX:ActiveProcessorCount`, `MALLOC_ARENA_MAX`, or `NativeMemoryTracking`.

The fix adds a small `JvmRuntimeDiagnostics` helper in the `profiles/killbill`
boot module and wires a single call into
`KillbillGuiceListener.startLifecycleStage2()` so the server logs its
container/JVM state exactly once at boot, with targeted hints when it detects
a containerized JDK 11+ environment that is missing the recommended knobs from
the issue.

## Change summary

- **`profiles/killbill/src/main/java/org/killbill/billing/server/diagnostics/JvmRuntimeDiagnostics.java`** (new) —
  Pure helper that collects a list of `Diagnostic{level, message}` records.
  - Always logs: VM name/version/vendor, spec version, JVM-visible CPUs, max
    heap, every active garbage collector, and whether `/proc/1/cgroup`
    indicates a container (`docker` / `kubepods` / `containerd` / `lxc`).
  - In a container, additionally emits:
    - a **WARN** when `availableProcessors() >
      CONTAINER_HIGH_CPU_WARN_THRESHOLD` (=16) **and** the process was started
      without `-XX:ActiveProcessorCount`, since that is the classic cgroup-blind
      thread-pool sizing failure mode called out in the issue;
    - an **INFO** hint to set `MALLOC_ARENA_MAX` when the env var is unset
      (glibc malloc arena bloat);
    - an **INFO** hint to set `-XX:NativeMemoryTracking=summary` when the flag
      is unset (so off-heap growth is observable in production).
  - All inputs (CPU count, container detection, JVM args, env lookup) flow
    through a package-private `Environment` interface so the helper has a clean
    test seam and can never throw at boot.

- **`profiles/killbill/src/main/java/org/killbill/billing/server/listeners/KillbillGuiceListener.java`** —
  One added import and one `JvmRuntimeDiagnostics.log()` call at the top of
  `startLifecycleStage2()`. No other behavior changed.

- **`profiles/killbill/src/test/java/org/killbill/billing/server/diagnostics/TestJvmRuntimeDiagnostics.java`** (new) —
  TestNG fast-group tests that exercise the helper through its `Environment`
  test seam.

No build files were touched and no other modules were modified.

## How the test exercises the new behavior

`TestJvmRuntimeDiagnostics` covers the four meaningful paths through the new
helper plus a boundary case:

| Test | Scenario | Assertion |
| --- | --- | --- |
| `testLogAndCollectAreSafeOnAnyHost` | Real host (default `Environment`) | `log()` and `collect()` never throw and always include the CPU line — the contract that lets `KillbillGuiceListener` call `log()` unconditionally at boot. |
| `testNonContainerizedEmitsNoContainerHints` | 64 CPUs, **not** containerized | No CPU warning, no `MALLOC_ARENA_MAX` hint, no NMT hint — bare-metal users do not get noise. |
| `testContainerizedWithUnpinnedCpuTriggersWarning` | Containerized with `17` JVM-visible CPUs and no `-XX:ActiveProcessorCount` | Emits a WARN containing both `ActiveProcessorCount` and the CPU count `17`. |
| `testContainerizedWithPinnedCpuSuppressesWarning` | Containerized with 48 CPUs but `-XX:ActiveProcessorCount` + `-XX:NativeMemoryTracking` set | No CPU warning, no NMT hint. |
| `testContainerizedWithoutMallocArenaMaxEmitsHint` | Containerized, `MALLOC_ARENA_MAX` unset | Emits the glibc malloc arena hint. |
| `testContainerizedWithMallocArenaMaxSuppressesHint` | Containerized, `MALLOC_ARENA_MAX=2` | Glibc malloc arena hint is suppressed. |
| `testCpuCountAtOrBelowThresholdDoesNotWarn` | Containerized, exactly `CONTAINER_HIGH_CPU_WARN_THRESHOLD` CPUs | No CPU warning (boundary — must be strictly above). |

Because the helper is fed via the `Environment` seam, the tests run identically
on macOS, Linux, ARM, or x86 — no embedded MySQL, no `/proc` access, no JVM
restart with flags.

## Build status

`mvn -pl profiles/killbill -am -DskipTests=true clean compile` and
`mvn -pl profiles/killbill -am -DskipTests=true test-compile` both produce:

```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  12.808 s
[INFO] Finished at: 2026-05-27T10:33:23-06:00
[INFO] ------------------------------------------------------------------------
```

(`-Dcheck.skip-*=true` was used to skip the non-compilation enforcer / scope /
spotbugs / duplicate-finder plugins — these require `mvn install` of the
test-jars to be available locally and are unrelated to the compilation gate.
The same flags are used by the project's own `bin/start-server` script.)

The TestNG tests themselves were not executed because the surefire phase
transitively pulls in the embedded MySQL ARM artifact, which is not published
for this host (`mysql-Mac_OS_X-aarch64.tar.gz` — explicitly called out in the
task constraints as a known environment issue). The new test class compiles
clean as part of `test-compile`.

## Confidence

**Medium-high.**

- The change is small, localized to a single boot module, and adds no
  dependencies.
- The helper is deliberately defensive (best-effort container detection,
  unconditional fallback to "not containerized" on non-Linux hosts, no
  exceptions on the boot path) and is exercised by tests that do not depend
  on host OS or container runtime.
- The interpretation of issue #2176 as "log container/JVM diagnostics at boot
  + emit hints for the specific footguns the issue lists" is a judgment call —
  the issue itself is open-ended ("Upgrade to java 17") and most of its
  bullet points are JVM flags rather than code. A reviewer may prefer the
  hints in a different place (e.g. in `start-server` shell scripts) or with
  different thresholds; both are easy follow-ups.
