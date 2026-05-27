/*
 * Copyright 2024 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.util.nodes;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;

/**
 * Captures JVM runtime diagnostics that are useful when running Kill Bill in
 * containerized deployments, especially after upgrading to JDK 17+.
 *
 * <p>The fields surfaced here mirror the tuning checklist from the
 * "JDK Memory Bloat in Containers" guidance referenced by issue #2176:
 * cgroup-aware CPU count, heap sizing, active garbage collector, native
 * memory tracking, and glibc malloc arenas. The class is deliberately
 * dependency-free so it can be invoked from any boot path without pulling
 * in Guice.</p>
 */
public final class JvmInfo {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final String javaVersion;
    private final String javaVendor;
    private final String javaVmName;
    private final int availableProcessors;
    private final long maxHeapBytes;
    private final long initialHeapBytes;
    private final List<String> garbageCollectors;
    private final List<String> inputArguments;
    private final Optional<String> activeProcessorCount;
    private final Optional<String> mallocArenaMax;
    private final boolean nativeMemoryTrackingEnabled;
    private final boolean useContainerSupport;
    private final List<String> warnings;

    JvmInfo(final String javaVersion,
            final String javaVendor,
            final String javaVmName,
            final int availableProcessors,
            final long maxHeapBytes,
            final long initialHeapBytes,
            final List<String> garbageCollectors,
            final List<String> inputArguments,
            final Optional<String> activeProcessorCount,
            final Optional<String> mallocArenaMax,
            final boolean nativeMemoryTrackingEnabled,
            final boolean useContainerSupport,
            final List<String> warnings) {
        this.javaVersion = javaVersion;
        this.javaVendor = javaVendor;
        this.javaVmName = javaVmName;
        this.availableProcessors = availableProcessors;
        this.maxHeapBytes = maxHeapBytes;
        this.initialHeapBytes = initialHeapBytes;
        this.garbageCollectors = Collections.unmodifiableList(new ArrayList<>(garbageCollectors));
        this.inputArguments = Collections.unmodifiableList(new ArrayList<>(inputArguments));
        this.activeProcessorCount = activeProcessorCount;
        this.mallocArenaMax = mallocArenaMax;
        this.nativeMemoryTrackingEnabled = nativeMemoryTrackingEnabled;
        this.useContainerSupport = useContainerSupport;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    /**
     * Captures a snapshot from the running JVM and environment.
     */
    public static JvmInfo capture() {
        final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        final MemoryUsage heap = memory.getHeapMemoryUsage();
        final List<String> gcs = ManagementFactory.getGarbageCollectorMXBeans().stream()
                                                  .map(GarbageCollectorMXBean::getName)
                                                  .collect(Collectors.toList());
        final List<String> args = runtime.getInputArguments();
        return build(
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vendor", "unknown"),
                System.getProperty("java.vm.name", "unknown"),
                Runtime.getRuntime().availableProcessors(),
                heap.getMax(),
                heap.getInit(),
                gcs,
                args,
                System::getenv);
    }

    /**
     * Visible for testing: build a JvmInfo from explicit inputs.
     */
    static JvmInfo build(final String javaVersion,
                         final String javaVendor,
                         final String javaVmName,
                         final int availableProcessors,
                         final long maxHeapBytes,
                         final long initialHeapBytes,
                         final List<String> garbageCollectors,
                         final List<String> inputArguments,
                         final Function<String, String> env) {
        final Optional<String> activeProcessorCount = findValuedArg(inputArguments, "-XX:ActiveProcessorCount=");
        final Optional<String> mallocArena = Optional.ofNullable(env.apply("MALLOC_ARENA_MAX"));
        final boolean nmt = findValuedArg(inputArguments, "-XX:NativeMemoryTracking=")
                .filter(v -> !"off".equalsIgnoreCase(v))
                .isPresent();
        final boolean containerSupportDisabled = inputArguments.contains("-XX:-UseContainerSupport");
        final boolean useContainerSupport = !containerSupportDisabled;

        final List<String> warnings = new ArrayList<>();
        if (containerSupportDisabled) {
            warnings.add("UseContainerSupport is disabled; the JVM may read host CPU/memory instead of cgroup limits.");
        }
        if (activeProcessorCount.isEmpty()) {
            warnings.add("-XX:ActiveProcessorCount is not set; the JVM will infer CPU count from cgroups, which has known detection bugs in some kernels.");
        }
        if (mallocArena.isEmpty()) {
            warnings.add("MALLOC_ARENA_MAX is not set; glibc may allocate one malloc arena per thread, inflating native RSS in containers. Consider exporting MALLOC_ARENA_MAX=2.");
        }
        if (!nmt) {
            warnings.add("-XX:NativeMemoryTracking is off; native memory regressions cannot be diagnosed without it. Consider -XX:NativeMemoryTracking=summary.");
        }
        if (maxHeapBytes > 0 && initialHeapBytes > 0 && initialHeapBytes != maxHeapBytes) {
            warnings.add("-Xms differs from -Xmx; pinning both to the same value avoids heap resize pauses in containers.");
        }

        return new JvmInfo(javaVersion, javaVendor, javaVmName, availableProcessors,
                           maxHeapBytes, initialHeapBytes, garbageCollectors, inputArguments,
                           activeProcessorCount, mallocArena, nmt, useContainerSupport, warnings);
    }

    private static Optional<String> findValuedArg(final List<String> args, final String prefix) {
        for (final String a : args) {
            if (a.startsWith(prefix)) {
                return Optional.of(a.substring(prefix.length()));
            }
        }
        return Optional.empty();
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getJavaVendor() {
        return javaVendor;
    }

    public String getJavaVmName() {
        return javaVmName;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public long getMaxHeapBytes() {
        return maxHeapBytes;
    }

    public long getInitialHeapBytes() {
        return initialHeapBytes;
    }

    public List<String> getGarbageCollectors() {
        return garbageCollectors;
    }

    public List<String> getInputArguments() {
        return inputArguments;
    }

    public Optional<String> getActiveProcessorCount() {
        return activeProcessorCount;
    }

    public Optional<String> getMallocArenaMax() {
        return mallocArenaMax;
    }

    public boolean isNativeMemoryTrackingEnabled() {
        return nativeMemoryTrackingEnabled;
    }

    public boolean isUseContainerSupport() {
        return useContainerSupport;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Emits an INFO line summarising the JVM and a WARN line for each tuning
     * recommendation that the current process does not satisfy.
     */
    public void logTo(final Logger logger) {
        if (logger == null) {
            return;
        }
        logger.info(String.format(Locale.ROOT,
                                  "JVM diagnostics: java=%s vendor=%s vm=%s processors=%d heapMax=%dMB heapInit=%dMB gc=%s containerSupport=%s nmt=%s activeProcessorCount=%s MALLOC_ARENA_MAX=%s",
                                  javaVersion,
                                  javaVendor,
                                  javaVmName,
                                  availableProcessors,
                                  maxHeapBytes < 0 ? -1 : maxHeapBytes / BYTES_PER_MB,
                                  initialHeapBytes < 0 ? -1 : initialHeapBytes / BYTES_PER_MB,
                                  garbageCollectors,
                                  useContainerSupport,
                                  nativeMemoryTrackingEnabled,
                                  activeProcessorCount.orElse("<unset>"),
                                  mallocArenaMax.orElse("<unset>")));
        for (final String w : warnings) {
            logger.warn("JVM tuning: {}", w);
        }
    }

    /**
     * Convenience wrapper to capture and log in one call from boot code.
     */
    public static JvmInfo captureAndLog(final Logger logger) {
        final JvmInfo info = capture();
        info.logTo(logger);
        return info;
    }

    // For tests that want to ignore real environment variables.
    static Function<String, String> emptyEnv() {
        return new EmptyEnv();
    }

    private static final class EmptyEnv implements Function<String, String> {
        private final Map<String, String> backing = Collections.emptyMap();

        @Override
        public String apply(final String name) {
            return backing.get(name);
        }
    }
}
