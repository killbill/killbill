/*
 * Copyright 2025 The Billing Project, LLC
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
 * Regression coverage for {@link UUIDs}, in particular the UUIDv7 support
 * introduced for issue #2156.
 */
public class TestUUIDs {

    @Test(groups = "fast")
    public void testRandomUUIDv4VersionAndVariant() {
        final UUID id = UUIDs.randomUUIDv4();
        Assert.assertEquals(id.version(), 4, "expected UUIDv4");
        Assert.assertEquals(id.variant(), 2, "expected IETF variant");
    }

    @Test(groups = "fast")
    public void testRandomUUIDv7VersionAndVariant() {
        final UUID id = UUIDs.randomUUIDv7();
        Assert.assertEquals(id.version(), 7, "expected UUIDv7");
        Assert.assertEquals(id.variant(), 2, "expected IETF variant");
    }

    @Test(groups = "fast")
    public void testRandomUUIDv7EncodesTimestampDeterministically() {
        // Use the package-private overload so the assertion is independent
        // of the JVM-wide monotonic counter (which other tests in this
        // class may have advanced past wall-clock time).
        final long millis = 0x0000018E_1B2C3D4EL; // arbitrary 48-bit value
        final UUID id = UUIDs.rndUUIDv7(millis);

        Assert.assertEquals(id.version(), 7);
        Assert.assertEquals(id.variant(), 2);
        Assert.assertEquals(UUIDs.timestampMillisFromV7(id), millis,
                            "UUIDv7 must embed the supplied unix-ms timestamp in the high 48 bits");
    }

    /**
     * UUIDv7 ids generated in sequence must be strictly increasing under
     * Java's signed {@code UUID.compareTo} for the entire 48-bit timestamp
     * range we care about. The monotonic millisecond counter in
     * {@link UUIDs#randomUUIDv7()} is what makes this hold even when many
     * ids are produced within the same wall-clock millisecond.
     */
    @Test(groups = "fast")
    public void testRandomUUIDv7IsMonotonic() {
        UUID prev = UUIDs.randomUUIDv7();
        for (int i = 0; i < 10_000; i++) {
            final UUID next = UUIDs.randomUUIDv7();
            Assert.assertTrue(next.compareTo(prev) > 0,
                              "v7 ids must be strictly increasing: " + prev + " -> " + next);
            prev = next;
        }
    }

    @Test(groups = "fast")
    public void testRandomUUIDv7IsUnique() {
        final int count = 10_000;
        final Set<UUID> seen = new HashSet<>(count * 2);
        for (int i = 0; i < count; i++) {
            Assert.assertTrue(seen.add(UUIDs.randomUUIDv7()), "duplicate UUIDv7 generated");
        }
    }

    @Test(groups = "fast", expectedExceptions = IllegalArgumentException.class)
    public void testTimestampExtractionRejectsNonV7() {
        UUIDs.timestampMillisFromV7(UUIDs.randomUUIDv4());
    }

    /**
     * The default of {@link UUIDs#randomUUID()} must remain UUIDv4 to keep
     * historical behavior; UUIDv7 is opt-in via the
     * {@code org.killbill.uuid.v7.enabled} system property (which is not
     * set in the test JVM).
     */
    @Test(groups = "fast")
    public void testRandomUUIDDefaultsToV4() {
        Assert.assertFalse(UUIDs.isUUIDv7Enabled(),
                           "UUIDv7 should be opt-in; toggle should be off by default in tests");
        Assert.assertEquals(UUIDs.randomUUID().version(), 4);
    }
}
