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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestJvmInfo {

    private static Function<String, String> envOf(final Map<String, String> values) {
        return name -> values.get(name);
    }

    @Test(groups = "fast")
    public void testCaptureReturnsRuntimeFacts() {
        final JvmInfo info = JvmInfo.capture();

        Assert.assertNotNull(info.getJavaVersion());
        Assert.assertFalse(info.getJavaVersion().isEmpty());
        Assert.assertNotNull(info.getJavaVmName());
        Assert.assertTrue(info.getAvailableProcessors() >= 1,
                          "availableProcessors should be at least 1, got " + info.getAvailableProcessors());
        Assert.assertNotNull(info.getGarbageCollectors());
        Assert.assertFalse(info.getGarbageCollectors().isEmpty(), "expected at least one GC MXBean");
        Assert.assertNotNull(info.getWarnings());
    }

    @Test(groups = "fast")
    public void testWarningsWhenContainerTuningIsMissing() {
        final JvmInfo info = JvmInfo.build(
                "17.0.10",
                "Eclipse Adoptium",
                "OpenJDK 64-Bit Server VM",
                8,
                512L * 1024L * 1024L,
                256L * 1024L * 1024L,
                List.of("G1 Young Generation", "G1 Old Generation"),
                Collections.emptyList(),
                envOf(new HashMap<>()));

        final List<String> warnings = info.getWarnings();
        Assert.assertTrue(warnings.stream().anyMatch(w -> w.contains("ActiveProcessorCount")),
                          "expected ActiveProcessorCount warning, got " + warnings);
        Assert.assertTrue(warnings.stream().anyMatch(w -> w.contains("MALLOC_ARENA_MAX")),
                          "expected MALLOC_ARENA_MAX warning, got " + warnings);
        Assert.assertTrue(warnings.stream().anyMatch(w -> w.contains("NativeMemoryTracking")),
                          "expected NativeMemoryTracking warning, got " + warnings);
        Assert.assertTrue(warnings.stream().anyMatch(w -> w.contains("-Xms")),
                          "expected -Xms/-Xmx warning, got " + warnings);

        Assert.assertTrue(info.getActiveProcessorCount().isEmpty());
        Assert.assertTrue(info.getMallocArenaMax().isEmpty());
        Assert.assertFalse(info.isNativeMemoryTrackingEnabled());
        Assert.assertTrue(info.isUseContainerSupport());
    }

    @Test(groups = "fast")
    public void testNoWarningsWhenAllRecommendationsSatisfied() {
        final Map<String, String> env = new HashMap<>();
        env.put("MALLOC_ARENA_MAX", "2");

        final long heap = 512L * 1024L * 1024L;
        final JvmInfo info = JvmInfo.build(
                "17.0.10",
                "Eclipse Adoptium",
                "OpenJDK 64-Bit Server VM",
                4,
                heap,
                heap,
                List.of("G1 Young Generation", "G1 Old Generation"),
                Arrays.asList("-XX:ActiveProcessorCount=4",
                              "-XX:NativeMemoryTracking=summary",
                              "-Xms512m",
                              "-Xmx512m"),
                envOf(env));

        Assert.assertEquals(info.getActiveProcessorCount().orElse(null), "4");
        Assert.assertEquals(info.getMallocArenaMax().orElse(null), "2");
        Assert.assertTrue(info.isNativeMemoryTrackingEnabled());
        Assert.assertTrue(info.isUseContainerSupport());
        Assert.assertEquals(info.getWarnings(), Collections.emptyList(),
                            "expected no warnings, got " + info.getWarnings());
    }

    @Test(groups = "fast")
    public void testContainerSupportDisabledIsFlagged() {
        final JvmInfo info = JvmInfo.build(
                "17.0.10",
                "Eclipse Adoptium",
                "OpenJDK 64-Bit Server VM",
                8,
                512L * 1024L * 1024L,
                512L * 1024L * 1024L,
                List.of("G1 Young Generation"),
                List.of("-XX:-UseContainerSupport",
                        "-XX:ActiveProcessorCount=2",
                        "-XX:NativeMemoryTracking=summary"),
                envOf(Map.of("MALLOC_ARENA_MAX", "2")));

        Assert.assertFalse(info.isUseContainerSupport());
        Assert.assertTrue(info.getWarnings().stream().anyMatch(w -> w.contains("UseContainerSupport")),
                          "expected UseContainerSupport warning, got " + info.getWarnings());
    }

    @Test(groups = "fast")
    public void testNativeMemoryTrackingOffIsTreatedAsDisabled() {
        final JvmInfo info = JvmInfo.build(
                "17.0.10",
                "Eclipse Adoptium",
                "OpenJDK 64-Bit Server VM",
                2,
                256L * 1024L * 1024L,
                256L * 1024L * 1024L,
                List.of("G1 Young Generation"),
                List.of("-XX:NativeMemoryTracking=off",
                        "-XX:ActiveProcessorCount=2"),
                envOf(Map.of("MALLOC_ARENA_MAX", "2")));

        Assert.assertFalse(info.isNativeMemoryTrackingEnabled());
        Assert.assertTrue(info.getWarnings().stream().anyMatch(w -> w.contains("NativeMemoryTracking")));
    }
}
