/*
 * Copyright 2020-2025 Equinix, Inc
 * Copyright 2014-2025 The Billing Project, LLC
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

package org.killbill.billing.util.jvm;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;

/**
 * Captures the running JVM's container/ergonomics-relevant settings and emits
 * advisory warnings for the Java 17+ best practices documented in issue #2176
 * (explicit processor count, glibc malloc arena cap, G1GC tuning, native memory
 * tracking). The warning logic is pure ({@link #computeWarnings(JvmEnvironmentInfo)})
 * so it can be exercised deterministically by tests independent of the JVM the
 * test harness happens to run on.
 */
public final class JvmEnvironmentInfo {

    private final int javaMajorVersion;
    private final String javaVersion;
    private final String vmName;
    private final String vmVendor;
    private final int availableProcessors;
    private final long maxHeapBytes;
    private final List<String> garbageCollectors;
    private final boolean containerized;
    private final String mallocArenaMax;
    private final String nativeMemoryTracking;
    private final String activeProcessorCount;
    private final String maxGcPauseMillis;

    public JvmEnvironmentInfo(final int javaMajorVersion,
                              final String javaVersion,
                              final String vmName,
                              final String vmVendor,
                              final int availableProcessors,
                              final long maxHeapBytes,
                              final List<String> garbageCollectors,
                              final boolean containerized,
                              final String mallocArenaMax,
                              final String nativeMemoryTracking,
                              final String activeProcessorCount,
                              final String maxGcPauseMillis) {
        this.javaMajorVersion = javaMajorVersion;
        this.javaVersion = javaVersion;
        this.vmName = vmName;
        this.vmVendor = vmVendor;
        this.availableProcessors = availableProcessors;
        this.maxHeapBytes = maxHeapBytes;
        this.garbageCollectors = garbageCollectors == null
                                 ? Collections.emptyList()
                                 : Collections.unmodifiableList(new ArrayList<>(garbageCollectors));
        this.containerized = containerized;
        this.mallocArenaMax = mallocArenaMax;
        this.nativeMemoryTracking = nativeMemoryTracking;
        this.activeProcessorCount = activeProcessorCount;
        this.maxGcPauseMillis = maxGcPauseMillis;
    }

    /**
     * Reads the running JVM's settings. Safe to call from any thread at any
     * point in the lifecycle; never throws.
     */
    public static JvmEnvironmentInfo capture() {
        return capture(System::getProperty, System::getenv);
    }

    static JvmEnvironmentInfo capture(final Function<String, String> properties,
                                      final Function<String, String> env) {
        final int major = parseJavaMajorVersion(properties.apply("java.specification.version"),
                                                properties.apply("java.version"));
        final List<String> gcs = ManagementFactory.getGarbageCollectorMXBeans().stream()
                                                  .map(GarbageCollectorMXBean::getName)
                                                  .collect(Collectors.toUnmodifiableList());
        return new JvmEnvironmentInfo(
                major,
                nullToUnknown(properties.apply("java.version")),
                nullToUnknown(properties.apply("java.vm.name")),
                nullToUnknown(properties.apply("java.vm.vendor")),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory(),
                gcs,
                detectContainer(),
                env.apply("MALLOC_ARENA_MAX"),
                properties.apply("java.nativeMemoryTracking"),
                properties.apply("jdk.internal.vm.options.ActiveProcessorCount"),
                properties.apply("jdk.internal.vm.options.MaxGCPauseMillis"));
    }

    static int parseJavaMajorVersion(final String specVersion, final String fallbackVersion) {
        final String candidate = specVersion != null ? specVersion : fallbackVersion;
        if (candidate == null) {
            return -1;
        }
        // "17", "17.0.9", "1.8.0_392"
        final String[] parts = candidate.split("\\.");
        try {
            final int first = Integer.parseInt(parts[0]);
            if (first == 1 && parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
            return first;
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private static boolean detectContainer() {
        // Heuristic: presence of /.dockerenv, or cgroup v1/v2 markers under /proc.
        try {
            if (Files.exists(Path.of("/.dockerenv"))) {
                return true;
            }
            final Path cgroup = Path.of("/proc/1/cgroup");
            if (Files.exists(cgroup)) {
                final String content = Files.readString(cgroup);
                if (content.contains("docker") || content.contains("kubepods") || content.contains("containerd")) {
                    return true;
                }
            }
        } catch (final Exception ignored) {
            // best-effort detection
        }
        return false;
    }

    private static String nullToUnknown(final String s) {
        return s == null ? "unknown" : s;
    }

    public int getJavaMajorVersion() {
        return javaMajorVersion;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getVmName() {
        return vmName;
    }

    public String getVmVendor() {
        return vmVendor;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public long getMaxHeapBytes() {
        return maxHeapBytes;
    }

    public List<String> getGarbageCollectors() {
        return garbageCollectors;
    }

    public boolean isContainerized() {
        return containerized;
    }

    public String getMallocArenaMax() {
        return mallocArenaMax;
    }

    public String getNativeMemoryTracking() {
        return nativeMemoryTracking;
    }

    public String getActiveProcessorCount() {
        return activeProcessorCount;
    }

    public String getMaxGcPauseMillis() {
        return maxGcPauseMillis;
    }

    /**
     * Pure: given a captured environment, return the list of advisory warnings
     * that apply. Tests pass synthetic inputs in to drive every branch.
     */
    public static List<String> computeWarnings(final JvmEnvironmentInfo info) {
        final List<String> warnings = new ArrayList<>();

        // Java 17+ on containers warrants extra scrutiny per issue #2176.
        final boolean java17OrLater = info.javaMajorVersion >= 17;

        if (info.containerized && isBlank(info.activeProcessorCount)) {
            warnings.add(String.format(Locale.ROOT,
                                       "Running in a container with Runtime.availableProcessors()=%d but "
                                       + "-XX:ActiveProcessorCount is not set. Setting it explicitly avoids "
                                       + "the JVM mis-reading the host CPU count when cgroup CPU shares are used.",
                                       info.availableProcessors));
        }

        if (info.containerized && isBlank(info.mallocArenaMax)) {
            warnings.add("Running in a container but MALLOC_ARENA_MAX is not set. glibc allocates one "
                         + "arena per thread (default cap = 8 * CPUs), which can balloon native RSS on "
                         + "thread-heavy services. Consider setting MALLOC_ARENA_MAX=2 or 4.");
        }

        if (java17OrLater && info.garbageCollectors.stream().anyMatch(JvmEnvironmentInfo::isG1)
            && isBlank(info.maxGcPauseMillis)) {
            warnings.add("Java 17+ defaulted to G1GC but -XX:MaxGCPauseMillis is not set. Either tune G1 "
                         + "(e.g. -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=16m) or switch to ParallelGC "
                         + "(-XX:+UseParallelGC) for the lowest native memory footprint.");
        }

        if (java17OrLater && isBlank(info.nativeMemoryTracking)) {
            warnings.add("Java 17+ is running without Native Memory Tracking. Enable with "
                         + "-XX:NativeMemoryTracking=summary to baseline heap, native, thread and GC overhead.");
        }

        return Collections.unmodifiableList(warnings);
    }

    private static boolean isG1(final String gc) {
        return gc != null && gc.toLowerCase(Locale.ROOT).contains("g1");
    }

    private static boolean isBlank(final String s) {
        return s == null || s.isBlank();
    }

    /** Single-line, human-readable summary suitable for an INFO log. */
    public String summary() {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("java", javaVersion);
        fields.put("major", javaMajorVersion);
        fields.put("vm", vmName);
        fields.put("vendor", vmVendor);
        fields.put("processors", availableProcessors);
        fields.put("maxHeapMB", maxHeapBytes == Long.MAX_VALUE ? "unbounded" : (maxHeapBytes / (1024L * 1024L)));
        fields.put("gc", garbageCollectors);
        fields.put("containerized", containerized);
        fields.put("MALLOC_ARENA_MAX", mallocArenaMax == null ? "unset" : mallocArenaMax);
        fields.put("nativeMemoryTracking", nativeMemoryTracking == null ? "off" : nativeMemoryTracking);
        return fields.entrySet().stream()
                     .map(e -> e.getKey() + "=" + e.getValue())
                     .collect(Collectors.joining(", ", "JVM environment: ", ""));
    }

    /**
     * Logs the captured environment at INFO and each advisory warning at WARN.
     * Never throws; intended to be called once at startup.
     */
    public void logTo(final Logger logger) {
        if (logger == null) {
            return;
        }
        try {
            logger.info(summary());
            for (final String w : computeWarnings(this)) {
                logger.warn("[JVM tuning, issue #2176] {}", w);
            }
        } catch (final RuntimeException ignored) {
            // Diagnostics must never break startup.
        }
    }
}
