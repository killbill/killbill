/*
 * Copyright 2020-2026 Equinix, Inc
 * Copyright 2014-2026 The Billing Project, LLC
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

package org.killbill.billing.server.diagnostics;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits JVM and container-awareness information at server boot so operators
 * can quickly spot misconfigurations that lead to native-memory bloat or
 * runaway thread pools when running on modern JDKs (JDK 11+ in containers).
 *
 * <p>See Kill Bill issue #2176 for the motivating discussion.</p>
 */
public final class JvmRuntimeDiagnostics {

    private static final Logger logger = LoggerFactory.getLogger(JvmRuntimeDiagnostics.class);

    // Most container-cgroup CPU quotas land well below this. When the JVM
    // reports many more visible CPUs than that from inside a container, it is
    // usually a sign that -XX:ActiveProcessorCount was not pinned and the JVM
    // sized its ForkJoinPool/G1 worker threads against the host instead of the
    // cgroup quota.
    static final int CONTAINER_HIGH_CPU_WARN_THRESHOLD = 16;

    enum Level { INFO, WARN }

    public static final class Diagnostic {
        public final Level level;
        public final String message;

        Diagnostic(final Level level, final String message) {
            this.level = level;
            this.message = message;
        }

        @Override
        public String toString() {
            return level + " " + message;
        }
    }

    private JvmRuntimeDiagnostics() {}

    /**
     * Emit diagnostics to the static SLF4J logger. Safe to call at boot from
     * any environment — never throws.
     */
    public static void log() {
        for (final Diagnostic d : collect()) {
            if (d.level == Level.WARN) {
                logger.warn(d.message);
            } else {
                logger.info(d.message);
            }
        }
    }

    /**
     * Collect diagnostics against the live JVM.
     */
    public static List<Diagnostic> collect() {
        return collect(defaultEnvironment());
    }

    static List<Diagnostic> collect(final Environment env) {
        final List<Diagnostic> out = new ArrayList<>();

        final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        final List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();

        final int availableProcessors = env.availableProcessors();
        final MemoryUsage heap = memory.getHeapMemoryUsage();
        final long maxHeapMb = heap.getMax() <= 0 ? -1 : heap.getMax() / (1024L * 1024L);

        out.add(info("Kill Bill JVM diagnostics (issue #2176):"));
        out.add(info("  Java VM         : " + runtime.getVmName() + " " + runtime.getVmVersion() + " (" + runtime.getVmVendor() + ")"));
        out.add(info("  Spec version    : " + runtime.getSpecVersion()));
        out.add(info("  Available CPUs  : " + availableProcessors + " (JVM-visible — should respect cgroup quota on JDK 11+)"));
        out.add(info("  Max heap        : " + (maxHeapMb < 0 ? "unbounded" : (maxHeapMb + " MB"))));
        for (final GarbageCollectorMXBean gc : gcs) {
            out.add(info("  GC              : " + gc.getName()));
        }

        final boolean inContainer = env.isContainerized();
        out.add(info("  Containerized   : " + inContainer));

        if (inContainer) {
            // Container-specific hints. These mirror the best-practices from
            // https://dzone.com/articles/jdk-memory-bloat-containers referenced
            // in the upstream issue.
            if (availableProcessors > CONTAINER_HIGH_CPU_WARN_THRESHOLD
                && !env.hasActiveProcessorCountFlag()) {
                out.add(new Diagnostic(Level.WARN,
                                       "Detected container with " + availableProcessors
                                       + " JVM-visible CPUs but -XX:ActiveProcessorCount is unset; "
                                       + "pin ActiveProcessorCount to your cgroup CPU quota to avoid oversized "
                                       + "ForkJoinPool/GC worker pools and the resulting native-memory bloat."));
            }
            if (env.getEnv("MALLOC_ARENA_MAX") == null) {
                out.add(info("Hint: set MALLOC_ARENA_MAX (e.g. 2) in containerized deployments to limit glibc "
                             + "malloc arena native-memory growth caused by thread proliferation."));
            }
            if (!env.hasNativeMemoryTrackingFlag()) {
                out.add(info("Hint: enable -XX:NativeMemoryTracking=summary to make off-heap growth observable when "
                             + "diagnosing container OOM-kills."));
            }
        }
        return out;
    }

    private static Diagnostic info(final String message) {
        return new Diagnostic(Level.INFO, message);
    }

    static boolean detectContainerized() {
        // Best-effort: only Linux containers have /proc/1/cgroup. Returns
        // false on macOS, Windows and bare-metal Linux without the file.
        final Path cgroup = Paths.get("/proc/1/cgroup");
        if (!Files.isReadable(cgroup)) {
            return false;
        }
        try {
            final String contents = Files.readString(cgroup);
            return contents.contains("docker")
                   || contents.contains("kubepods")
                   || contents.contains("containerd")
                   || contents.contains("/lxc/");
        } catch (final IOException e) {
            return false;
        }
    }

    private static Environment defaultEnvironment() {
        final List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        return new Environment() {
            @Override
            public int availableProcessors() {
                return Runtime.getRuntime().availableProcessors();
            }

            @Override
            public boolean isContainerized() {
                return detectContainerized();
            }

            @Override
            public boolean hasActiveProcessorCountFlag() {
                return jvmArgs.stream().anyMatch(a -> a.startsWith("-XX:ActiveProcessorCount"));
            }

            @Override
            public boolean hasNativeMemoryTrackingFlag() {
                return jvmArgs.stream().anyMatch(a -> a.startsWith("-XX:NativeMemoryTracking"));
            }

            @Override
            public String getEnv(final String name) {
                return System.getenv(name);
            }
        };
    }

    /**
     * Test seam — allows the diagnostics to be exercised without spinning up a
     * real container or restarting the JVM with different flags.
     */
    interface Environment {
        int availableProcessors();

        boolean isContainerized();

        boolean hasActiveProcessorCountFlag();

        boolean hasNativeMemoryTrackingFlag();

        String getEnv(String name);

        static Environment from(final int cpus,
                                final boolean container,
                                final boolean activeProcessorCount,
                                final boolean nativeMemoryTracking,
                                final Function<String, String> envLookup) {
            return new Environment() {
                @Override
                public int availableProcessors() {
                    return cpus;
                }

                @Override
                public boolean isContainerized() {
                    return container;
                }

                @Override
                public boolean hasActiveProcessorCountFlag() {
                    return activeProcessorCount;
                }

                @Override
                public boolean hasNativeMemoryTrackingFlag() {
                    return nativeMemoryTracking;
                }

                @Override
                public String getEnv(final String name) {
                    return envLookup.apply(name);
                }
            };
        }
    }
}
