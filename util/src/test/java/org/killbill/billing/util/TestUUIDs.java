/*
 * Copyright 2024-2026 The Billing Project, LLC
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Regression tests for issue #2156 — adopt RFC 9562 UUIDv7 for system identifiers.
 */
public class TestUUIDs {

    @Test(groups = "fast")
    public void testRandomUUIDIsVersion7() {
        final UUID id = UUIDs.randomUUID();
        Assert.assertEquals(id.version(), 7, "UUIDs.randomUUID() must yield a UUIDv7");
        Assert.assertEquals(id.variant(), 2, "UUID variant must be IETF (RFC 4122/9562)");
    }

    @Test(groups = "fast")
    public void testRandomUUIDv7Explicit() {
        final UUID id = UUIDs.randomUUIDv7();
        Assert.assertEquals(id.version(), 7);
        Assert.assertEquals(id.variant(), 2);
    }

    @Test(groups = "fast")
    public void testRandomUUIDv4StillAvailable() {
        final UUID id = UUIDs.randomUUIDv4();
        Assert.assertEquals(id.version(), 4, "randomUUIDv4() must remain a UUIDv4");
        Assert.assertEquals(id.variant(), 2);
    }

    @Test(groups = "fast")
    public void testV7TimestampEncodesCurrentMillis() {
        final long before = System.currentTimeMillis();
        final UUID id = UUIDs.randomUUIDv7();
        final long after = System.currentTimeMillis();

        final long ts = UUIDs.unixTimestampMillis(id);
        Assert.assertTrue(ts >= before && ts <= after,
                          "Encoded ts " + ts + " was not in [" + before + ", " + after + "]");
    }

    @Test(groups = "fast", expectedExceptions = IllegalArgumentException.class)
    public void testUnixTimestampRejectsNonV7() {
        UUIDs.unixTimestampMillis(UUIDs.randomUUIDv4());
    }

    /**
     * The core property we care about for B-tree locality: successive UUIDs
     * emitted by the same thread must be strictly increasing under the natural
     * (unsigned 128-bit, big-endian) byte ordering — i.e. the order a database
     * would see when treating the UUID as a binary primary key.
     */
    @Test(groups = "fast")
    public void testV7MonotonicallyOrderedWithinSameThread() {
        final int n = 10_000;
        final UUID[] ids = new UUID[n];
        for (int i = 0; i < n; i++) {
            ids[i] = UUIDs.randomUUIDv7();
        }

        for (int i = 1; i < n; i++) {
            final UUID a = ids[i - 1];
            final UUID b = ids[i];
            final int cmp = compareUnsigned128(a, b);
            Assert.assertTrue(cmp < 0,
                              "UUIDv7s not monotonic at i=" + i + ": " + a + " >= " + b);
        }
    }

    @Test(groups = "fast")
    public void testV7Uniqueness() {
        final int n = 50_000;
        final Set<UUID> seen = new HashSet<>(n);
        for (int i = 0; i < n; i++) {
            Assert.assertTrue(seen.add(UUIDs.randomUUIDv7()),
                              "Duplicate UUIDv7 generated within a single thread");
        }
    }

    private static int compareUnsigned128(final UUID a, final UUID b) {
        final int msbCmp = Long.compareUnsigned(a.getMostSignificantBits(),
                                                b.getMostSignificantBits());
        if (msbCmp != 0) {
            return msbCmp;
        }
        return Long.compareUnsigned(a.getLeastSignificantBits(),
                                    b.getLeastSignificantBits());
    }
}
