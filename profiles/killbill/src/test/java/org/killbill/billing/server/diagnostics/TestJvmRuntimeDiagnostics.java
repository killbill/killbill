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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.server.diagnostics.JvmRuntimeDiagnostics.Diagnostic;
import org.killbill.billing.server.diagnostics.JvmRuntimeDiagnostics.Environment;
import org.killbill.billing.server.diagnostics.JvmRuntimeDiagnostics.Level;

public class TestJvmRuntimeDiagnostics {

    @Test(groups = "fast")
    public void testLogAndCollectAreSafeOnAnyHost() {
        // The static entry points must never throw at boot regardless of the
        // host OS / JVM flags / env vars — this is the contract that lets
        // KillbillGuiceListener call log() unconditionally.
        JvmRuntimeDiagnostics.log();
        final List<Diagnostic> diagnostics = JvmRuntimeDiagnostics.collect();
        Assert.assertFalse(diagnostics.isEmpty(), "Diagnostics must always include the basic JVM lines");
        Assert.assertTrue(containsInfo(diagnostics, "Available CPUs"),
                          "CPU count must always be reported");
    }

    @Test(groups = "fast")
    public void testNonContainerizedEmitsNoContainerHints() {
        final List<Diagnostic> diagnostics = JvmRuntimeDiagnostics.collect(
                Environment.from(64, false, false, false, noEnv()));

        Assert.assertFalse(containsWarn(diagnostics, "ActiveProcessorCount"),
                           "Non-containerized hosts must not get the CPU warning");
        Assert.assertFalse(containsInfo(diagnostics, "MALLOC_ARENA_MAX"),
                           "Non-containerized hosts must not get the glibc malloc arena hint");
        Assert.assertFalse(containsInfo(diagnostics, "NativeMemoryTracking"),
                           "Non-containerized hosts must not get the NMT hint");
    }

    @Test(groups = "fast")
    public void testContainerizedWithUnpinnedCpuTriggersWarning() {
        final int cpus = JvmRuntimeDiagnostics.CONTAINER_HIGH_CPU_WARN_THRESHOLD + 1;
        final List<Diagnostic> diagnostics = JvmRuntimeDiagnostics.collect(
                Environment.from(cpus, true, false, false, noEnv()));

        Assert.assertTrue(containsWarn(diagnostics, "ActiveProcessorCount"),
                          "Containerized hosts with many JVM-visible CPUs must be warned to pin ActiveProcessorCount");
        Assert.assertTrue(containsWarn(diagnostics, String.valueOf(cpus)),
                          "Warning must include the actual JVM-visible CPU count");
    }

    @Test(groups = "fast")
    public void testContainerizedWithPinnedCpuSuppressesWarning() {
        final List<Diagnostic> diagnostics = JvmRuntimeDiagnostics.collect(
                Environment.from(JvmRuntimeDiagnostics.CONTAINER_HIGH_CPU_WARN_THRESHOLD + 32,
                                 true, true, true, noEnv()));

        Assert.assertFalse(containsWarn(diagnostics, "ActiveProcessorCount"),
                           "Pinning -XX:ActiveProcessorCount must suppress the CPU warning");
        Assert.assertFalse(containsInfo(diagnostics, "NativeMemoryTracking"),
                           "Setting -XX:NativeMemoryTracking must suppress the NMT hint");
    }

    @Test(groups = "fast")
    public void testContainerizedWithoutMallocArenaMaxEmitsHint() {
        final List<Diagnostic> diagnostics = JvmRuntimeDiagnostics.collect(
                Environment.from(4, true, true, true, noEnv()));

        Assert.assertTrue(containsInfo(diagnostics, "MALLOC_ARENA_MAX"),
                          "Containerized hosts without MALLOC_ARENA_MAX must receive the glibc malloc hint");
    }

    @Test(groups = "fast")
    public void testContainerizedWithMallocArenaMaxSuppressesHint() {
        final Function<String, String> envLookup = Map.of("MALLOC_ARENA_MAX", "2")::get;
        final List<Diagnostic> diagnostics = JvmRuntimeDiagnostics.collect(
                Environment.from(4, true, true, true, envLookup));

        Assert.assertFalse(containsInfo(diagnostics, "MALLOC_ARENA_MAX"),
                           "Setting MALLOC_ARENA_MAX must suppress the glibc malloc hint");
    }

    @Test(groups = "fast")
    public void testCpuCountAtOrBelowThresholdDoesNotWarn() {
        // Boundary: exactly at threshold should not warn, only strictly above.
        final List<Diagnostic> diagnostics = JvmRuntimeDiagnostics.collect(
                Environment.from(JvmRuntimeDiagnostics.CONTAINER_HIGH_CPU_WARN_THRESHOLD,
                                 true, false, true, noEnv()));

        Assert.assertFalse(containsWarn(diagnostics, "ActiveProcessorCount"),
                           "CPU count at or below threshold must not warn");
    }

    private static boolean containsInfo(final List<Diagnostic> diagnostics, final String needle) {
        return diagnostics.stream()
                          .anyMatch(d -> d.level == Level.INFO && d.message.contains(needle));
    }

    private static boolean containsWarn(final List<Diagnostic> diagnostics, final String needle) {
        return diagnostics.stream()
                          .anyMatch(d -> d.level == Level.WARN && d.message.contains(needle));
    }

    private static Function<String, String> noEnv() {
        return name -> null;
    }
}
