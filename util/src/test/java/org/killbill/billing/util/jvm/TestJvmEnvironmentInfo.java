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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestJvmEnvironmentInfo {

    private static final Logger logger = LoggerFactory.getLogger(TestJvmEnvironmentInfo.class);

    @Test(groups = "fast")
    public void testParseJavaMajorVersionForJdk17Style() {
        assertEquals(JvmEnvironmentInfo.parseJavaMajorVersion("17", "17.0.9"), 17);
        assertEquals(JvmEnvironmentInfo.parseJavaMajorVersion(null, "17.0.9"), 17);
        assertEquals(JvmEnvironmentInfo.parseJavaMajorVersion("21", null), 21);
    }

    @Test(groups = "fast")
    public void testParseJavaMajorVersionForLegacy1xStyle() {
        assertEquals(JvmEnvironmentInfo.parseJavaMajorVersion(null, "1.8.0_392"), 8);
        assertEquals(JvmEnvironmentInfo.parseJavaMajorVersion("1.8", "1.8.0_392"), 8);
    }

    @Test(groups = "fast")
    public void testParseJavaMajorVersionForGarbageInput() {
        assertEquals(JvmEnvironmentInfo.parseJavaMajorVersion(null, null), -1);
        assertEquals(JvmEnvironmentInfo.parseJavaMajorVersion("not-a-number", null), -1);
    }

    @Test(groups = "fast")
    public void testCaptureOnRunningJvmReturnsRealValues() {
        final JvmEnvironmentInfo info = JvmEnvironmentInfo.capture();
        assertNotNull(info);
        // Project source target is Java 11, so the test JVM must be >= 11.
        assertTrue(info.getJavaMajorVersion() >= 11,
                   "Expected Java 11+, was " + info.getJavaMajorVersion());
        assertTrue(info.getAvailableProcessors() >= 1);
        assertTrue(info.getMaxHeapBytes() > 0L);
        assertNotNull(info.getGarbageCollectors());
        assertNotNull(info.summary());
        assertTrue(info.summary().startsWith("JVM environment: "));
        // logTo must never throw on a real JVM.
        info.logTo(logger);
    }

    @Test(groups = "fast")
    public void testWarningsTriggerForContainerizedJava17WithG1AndDefaults() {
        // Containerized Java 17 with G1 and no tuning flags should trigger all four advisories.
        final JvmEnvironmentInfo info = new JvmEnvironmentInfo(
                17,
                "17.0.9",
                "OpenJDK 64-Bit Server VM",
                "Eclipse Adoptium",
                4,
                512L * 1024 * 1024,
                List.of("G1 Young Generation", "G1 Old Generation"),
                true,                       // containerized
                null,                       // MALLOC_ARENA_MAX unset
                null,                       // NMT off
                null,                       // ActiveProcessorCount unset
                null);                      // MaxGCPauseMillis unset

        final List<String> warnings = JvmEnvironmentInfo.computeWarnings(info);
        assertEquals(warnings.size(), 4, "warnings: " + warnings);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("ActiveProcessorCount")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("MALLOC_ARENA_MAX")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("MaxGCPauseMillis")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("NativeMemoryTracking")));
    }

    @Test(groups = "fast")
    public void testWarningsSuppressedWhenAllRecommendationsApplied() {
        // Containerized Java 17 with all flags set => no advisories.
        final JvmEnvironmentInfo info = new JvmEnvironmentInfo(
                17,
                "17.0.9",
                "OpenJDK 64-Bit Server VM",
                "Eclipse Adoptium",
                4,
                512L * 1024 * 1024,
                List.of("G1 Young Generation", "G1 Old Generation"),
                true,
                "2",          // MALLOC_ARENA_MAX
                "summary",    // NMT
                "4",          // ActiveProcessorCount
                "200");       // MaxGCPauseMillis

        assertTrue(JvmEnvironmentInfo.computeWarnings(info).isEmpty());
    }

    @Test(groups = "fast")
    public void testNonContainerJava11SkipsContainerAndJava17Warnings() {
        // Non-containerized Java 11 should not trip container or Java-17 specific warnings.
        final JvmEnvironmentInfo info = new JvmEnvironmentInfo(
                11,
                "11.0.20",
                "OpenJDK 64-Bit Server VM",
                "Eclipse Adoptium",
                8,
                1024L * 1024 * 1024,
                List.of("G1 Young Generation", "G1 Old Generation"),
                false,
                null,
                null,
                null,
                null);

        final List<String> warnings = JvmEnvironmentInfo.computeWarnings(info);
        assertTrue(warnings.isEmpty(), "unexpected warnings: " + warnings);
    }

    @Test(groups = "fast")
    public void testParallelGcOnJava17DoesNotTriggerG1Warning() {
        final JvmEnvironmentInfo info = new JvmEnvironmentInfo(
                17,
                "17.0.9",
                "OpenJDK 64-Bit Server VM",
                "Eclipse Adoptium",
                4,
                512L * 1024 * 1024,
                List.of("PS Scavenge", "PS MarkSweep"),
                false,
                null,
                "summary",
                null,
                null);

        final List<String> warnings = JvmEnvironmentInfo.computeWarnings(info);
        assertFalse(warnings.stream().anyMatch(w -> w.contains("MaxGCPauseMillis")),
                    "ParallelGC should not trigger G1 advisory: " + warnings);
    }

    @Test(groups = "fast")
    public void testSummaryFormatsUnboundedHeap() {
        final JvmEnvironmentInfo info = new JvmEnvironmentInfo(
                17, "17", "vm", "vendor", 2,
                Long.MAX_VALUE, List.of("G1 Young Generation"),
                false, null, null, null, null);
        assertTrue(info.summary().contains("maxHeapMB=unbounded"));
    }
}
