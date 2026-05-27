/*
 * Copyright 2026 The Billing Project, LLC
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

package org.killbill.billing.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Regression coverage for the UUIDv7 adoption tracked by issue #2156.
 *
 * UUIDv7 (RFC 9562) carries a 48-bit Unix-ms timestamp in its leading bytes
 * so that identifiers minted in chronological order sort lexicographically
 * the same way — which is what makes them friendly to B-tree primary keys.
 * The tests here lock in three properties that callers depend on:
 *   1. {@link UUIDs#randomUUID()} now returns version-7 identifiers,
 *   2. the leading timestamp matches wall-clock time,
 *   3. successive calls (including within the same millisecond) are
 *      strictly monotonically increasing.
 */
public class TestUUIDs {

    @Test(groups = "fast")
    public void testRandomUUIDReturnsVersion7() {
        for (int i = 0; i < 1_000; i++) {
            final UUID id = UUIDs.randomUUID();
            Assert.assertEquals(id.version(), 7, "Expected UUIDv7, got: " + id);
            Assert.assertEquals(id.variant(), 2, "Expected IETF variant, got: " + id);
        }
    }

    @Test(groups = "fast")
    public void testExplicitV7HelperReturnsVersion7() {
        final UUID id = UUIDs.randomUUIDv7();
        Assert.assertEquals(id.version(), 7);
        Assert.assertEquals(id.variant(), 2);
    }

    @Test(groups = "fast")
    public void testLegacyV4HelperStillReturnsVersion4() {
        // The v4 generator is preserved for callers that explicitly want an
        // identifier with no temporal correlation.
        final UUID id = UUIDs.randomUUIDv4();
        Assert.assertEquals(id.version(), 4);
        Assert.assertEquals(id.variant(), 2);
    }

    @Test(groups = "fast")
    public void testEmbeddedTimestampMatchesWallClock() {
        final long before = System.currentTimeMillis();
        final UUID id = UUIDs.randomUUID();
        final long after = System.currentTimeMillis();

        // For UUIDv7, the high 48 bits of the most-significant-long carry the
        // Unix-ms timestamp.
        final long embedded = id.getMostSignificantBits() >>> 16;

        Assert.assertTrue(embedded >= before,
                          "Embedded timestamp " + embedded + " precedes wall clock " + before);
        Assert.assertTrue(embedded <= after + 1,
                          "Embedded timestamp " + embedded + " is past wall clock " + after);
    }

    @Test(groups = "fast")
    public void testMonotonicWithinSameMillisecond() {
        // Generate a burst tight enough that many calls land in the same ms;
        // each successive id must still compare strictly greater than the
        // previous one. This is the property that makes UUIDv7 useful as a
        // primary key — pure v4 would fail this test.
        final int n = 5_000;
        UUID previous = UUIDs.randomUUID();
        for (int i = 1; i < n; i++) {
            final UUID next = UUIDs.randomUUID();
            Assert.assertTrue(compareUnsigned(next, previous) > 0,
                              "UUIDv7 ordering violated at i=" + i +
                              ": previous=" + previous + " next=" + next);
            previous = next;
        }
    }

    @Test(groups = "fast")
    public void testUniquenessUnderConcurrency() throws Exception {
        // Multi-threaded burst — exercises the shared monotonic counter and
        // confirms no collisions slip through.
        final int threads = 8;
        final int perThread = 2_000;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final List<Future<List<UUID>>> futures = new ArrayList<>(threads);
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(new Callable<List<UUID>>() {
                    @Override
                    public List<UUID> call() {
                        final List<UUID> local = new ArrayList<>(perThread);
                        for (int i = 0; i < perThread; i++) {
                            local.add(UUIDs.randomUUID());
                        }
                        return local;
                    }
                }));
            }

            final Set<UUID> all = new HashSet<>(threads * perThread);
            for (final Future<List<UUID>> f : futures) {
                for (final UUID id : f.get(30, TimeUnit.SECONDS)) {
                    Assert.assertEquals(id.version(), 7);
                    Assert.assertTrue(all.add(id), "Duplicate UUID generated: " + id);
                }
            }
            Assert.assertEquals(all.size(), threads * perThread);
        } finally {
            pool.shutdownNow();
        }
    }

    // UUIDs are unsigned 128-bit values, but UUID#compareTo treats the longs
    // as signed which inverts ordering around the sign bit. For UUIDv7 the
    // timestamp lives in the top 48 bits, so the top bit can flip across an
    // ordinary millisecond boundary — we need an unsigned comparison.
    private static int compareUnsigned(final UUID a, final UUID b) {
        final int hi = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (hi != 0) {
            return hi;
        }
        return Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
