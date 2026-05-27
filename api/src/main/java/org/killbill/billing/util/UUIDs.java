/*
 * Copyright 2015-2016 Groupon, Inc
 * Copyright 2015-2016 The Billing Project, LLC
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/**
 * UUIDs helper.
 *
 * @author kares
 */
public abstract class UUIDs {

    /**
     * Generate a random Kill Bill identifier.
     *
     * <p>Since issue #2156 this returns an RFC 9562 (formerly draft-ietf-uuidrev-rfc4122bis)
     * <strong>version 7</strong> UUID — a 128-bit value whose top 48 bits encode the
     * Unix epoch in milliseconds, followed by a 4-bit version field, 74 bits of
     * randomness, and a 2-bit variant field. The result is still a {@link UUID}
     * and is wire-/storage-compatible with the previously generated v4 values, but
     * is time-ordered, which yields significantly better B-tree locality when used
     * as a database key (see https://www.rfc-editor.org/rfc/rfc9562 §5.7).</p>
     *
     * <p>Within a single thread, two successive calls are guaranteed to produce
     * lexicographically increasing UUIDs (when compared as unsigned 128-bit byte
     * strings), even if invoked within the same millisecond.</p>
     */
    public static UUID randomUUID() { return rndUUIDv7(); }

    /**
     * Generate a legacy RFC 4122 version 4 (purely random) UUID. Retained for
     * call sites that explicitly require a non-time-ordered identifier, e.g.
     * security tokens or test fixtures whose ordering must not leak the wall
     * clock. New code should prefer {@link #randomUUID()}.
     */
    public static UUID randomUUIDv4() { return rndUUIDv4(); }

    /**
     * Explicit accessor for the new RFC 9562 version 7 generator. Equivalent
     * to {@link #randomUUID()} but self-documenting at the call site.
     */
    public static UUID randomUUIDv7() { return rndUUIDv7(); }

    /**
     * Extract the embedded Unix timestamp (in milliseconds since the epoch) from
     * a v7 UUID. Throws {@link IllegalArgumentException} if the supplied UUID is
     * not version 7.
     */
    public static long unixTimestampMillis(final UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("Not a UUIDv7: " + uuid);
        }
        // Top 48 bits of the most-significant long.
        return uuid.getMostSignificantBits() >>> 16;
    }

    public static void setRandom(final Random random) {
        threadRandom.set(random);
    }

    public static Random getRandom() {
        return threadRandom.get();
    }

    // RFC 9562 UUIDv7 layout (bit indexes within the 128-bit value, MSB first):
    //   [ 0..47]  unix_ts_ms  (48 bits)
    //   [48..51]  version     (4 bits) = 0b0111
    //   [52..63]  rand_a      (12 bits)
    //   [64..65]  variant     (2 bits) = 0b10
    //   [66..127] rand_b      (62 bits)
    //
    // Monotonicity within a single thread is preserved using the "monotonic
    // random" method (RFC 9562 §6.2 method 1): on a same-ms call we increment
    // the previously-emitted random payload by one, carrying through rand_a
    // and finally bumping the timestamp if the entire 74-bit random space
    // wraps (in practice unreachable).
    private static UUID rndUUIDv7() {
        final long now = System.currentTimeMillis();
        final long[] state = threadV7State.get();

        long ts;
        long msb;
        long lsb;

        if (now > state[0]) {
            ts = now;
            final Random random = threadRandom.get();
            final byte[] bytes = new byte[10]; // 80 random bits, we keep 74
            random.nextBytes(bytes);
            final long randA = (((bytes[0] & 0xffL) << 8) | (bytes[1] & 0xffL)) & 0x0FFFL;
            long randB = 0L;
            for (int i = 2; i < 10; i++) {
                randB = (randB << 8) | (bytes[i] & 0xffL);
            }
            randB &= 0x3FFFFFFFFFFFFFFFL;
            msb = (ts << 16) | (0x7L << 12) | randA;
            lsb = (0x2L << 62) | randB;
        } else {
            // Clock did not advance (same ms, or it ran backwards). Reuse the
            // last timestamp and increment the 74-bit random payload so the
            // emitted UUID remains strictly greater than the previous one.
            ts = state[0];
            msb = state[1];
            lsb = state[2];

            long randB = (lsb & 0x3FFFFFFFFFFFFFFFL) + 1L;
            if ((randB & ~0x3FFFFFFFFFFFFFFFL) != 0L) {
                randB = 0L;
                long randA = (msb & 0x0FFFL) + 1L;
                if ((randA & ~0x0FFFL) != 0L) {
                    // Entire 74-bit random space exhausted within one ms. Roll
                    // the timestamp forward; with current clocks this is unreachable.
                    randA = 0L;
                    ts++;
                }
                msb = (ts << 16) | (0x7L << 12) | randA;
            }
            lsb = (0x2L << 62) | randB;
        }

        state[0] = ts;
        state[1] = msb;
        state[2] = lsb;

        return new UUID(msb, lsb);
    }

    private static final ThreadLocal<long[]> threadV7State =
        new ThreadLocal<long[]>() {
            @Override
            protected long[] initialValue() {
                return new long[]{ -1L, 0L, 0L };
            }
    };

    private static UUID rndUUIDv4() {
        // ~ return UUID.randomUUID() :
        final Random random = threadRandom.get();

        final byte[] uuid = new byte[16];
        random.nextBytes(uuid);
        uuid[6]  &= 0x0f;  /* clear version        */
        uuid[6]  |= 0x40;  /* set to version 4     */
        uuid[8]  &= 0x3f;  /* clear variant        */
        uuid[8]  |= 0x80;  /* set to IETF variant  */

        long msb = 0;
        msb = (msb << 8) | (uuid[0] & 0xff);
        msb = (msb << 8) | (uuid[1] & 0xff);
        msb = (msb << 8) | (uuid[2] & 0xff);
        msb = (msb << 8) | (uuid[3] & 0xff);
        msb = (msb << 8) | (uuid[4] & 0xff);
        msb = (msb << 8) | (uuid[5] & 0xff);
        msb = (msb << 8) | (uuid[6] & 0xff);
        msb = (msb << 8) | (uuid[7] & 0xff);

        long lsb = 0;
        lsb = (lsb << 8) | (uuid[8] & 0xff);
        lsb = (lsb << 8) | (uuid[9] & 0xff);
        lsb = (lsb << 8) | (uuid[10] & 0xff);
        lsb = (lsb << 8) | (uuid[11] & 0xff);
        lsb = (lsb << 8) | (uuid[12] & 0xff);
        lsb = (lsb << 8) | (uuid[13] & 0xff);
        lsb = (lsb << 8) | (uuid[14] & 0xff);
        lsb = (lsb << 8) | (uuid[15] & 0xff);

        return new UUID(msb, lsb);
    }

    private static final ThreadLocal<Random> threadRandom =
        new ThreadLocal<Random>() {
            protected Random initialValue() {
                return new LightSecureRandom(); // new SecureRandom();
            }
    };

    /**
     * An implementation of SecureRandom inspired by bouncy-castle's own for
     * light-weight APIs (JDK 1.0, and J2ME).
     *
     * Random generation is based on the traditional SHA1 with
     * counter. Calling setSeed will always increase the entropy of the hash.
     */
    // NOTE: assumes non-concurrent use (generator calls should be synchronized)
    private static class LightSecureRandom extends Random {

        private static abstract class SeederHolder {
            static final SecureRandom seeder;
            /* some related info from the JDK itself :
            # By default, an attempt is made to use the entropy gathering device
            # specified by the "securerandom.source" Security property.  If an
            # exception occurs while accessing the specified URL:
            #
            #     SHA1PRNG:
            #         the traditional system/thread activity algorithm will be used.
            #
            #     NativePRNG:
            #         a default value of /dev/random will be used.  If neither
                      are available, the implementation will be disabled.
            #         "file" is the only currently supported protocol type.
            #
            # The entropy gathering device can also be specified with the System
              property "java.security.egd". For example:
            #
            #   % java -Djava.security.egd=file:/dev/random MainClass
            #
            # Specifying this System property will override the
            # "securerandom.source" Security property.
            #
            # In addition, if "file:/dev/random" or "file:/dev/urandom" is
            # specified, the "NativePRNG" implementation will be preferred over
            # SHA1PRNG in the Sun provider.
            #
            securerandom.source=file:/dev/random
            */
            static {
                SecureRandom random;
                // if the securerandom.seed preference is set to file:/dev/urandom
                // (default) for Linux, new SecureRandom() returns a NativePRNG,
                // which generateSeed()s from /dev/xxx (thus might block terribly)
                // ... instead we explicitly use a SHA1PRNG seeder which will only
                // seeds itself on initialization (possibly from /dev/urandom)
                try {
                    random = SecureRandom.getInstance("SHA1PRNG");
                }
                catch (NoSuchAlgorithmException e) {
                    random = new SecureRandom(); // should never happen
                }
                seeder = random;
            }
        }

        private final DigestRandomGenerator generator;

        LightSecureRandom() {
            this( sha1Generator() );
            setSeed( SeederHolder.seeder.generateSeed( generator.getDigestLength() ) );
        }

        LightSecureRandom(final byte[] inSeed) {
            this( sha1Generator() );
            setSeed(inSeed);
        }

        private LightSecureRandom(DigestRandomGenerator generator) {
            super(0);
            this.generator = generator;
        }

        private static DigestRandomGenerator sha1Generator() {
            try {
                return new DigestRandomGenerator(MessageDigest.getInstance("SHA-1"));
            }
            catch (NoSuchAlgorithmException ex) {
                throw new Error("unexpeced missing SHA-1 digest", ex);
            }
        }

        @Override
        public void setSeed(final long seed) {
            if ( seed != 0 ) { // to avoid problems with Random calling setSeed in construction
                generator.addSeedMaterial(seed);
            }
        }

        public void setSeed(final byte[] seed) {
            generator.addSeedMaterial(seed);
        }

        @Override
        public void nextBytes(byte[] bytes) {
            generator.nextBytes(bytes);
        }

        @Override
        public int nextInt() {
            final byte[] intBytes = new byte[4];

            nextBytes(intBytes);

            int result = 0;

            for ( int i = 0; i < 4; i++ ) {
                result = (result << 8) + (intBytes[i] & 0xff);
            }

            return result;
        }

        @Override
        protected final int next(int numBits) {
            int size = (numBits + 7) / 8;
            byte[] bytes = new byte[size];

            nextBytes(bytes);

            int result = 0;

            for (int i = 0; i < size; i++)
            {
                result = (result << 8) + (bytes[i] & 0xff);
            }

            return result & ((1 << numBits) - 1);
        }

    }

    private static class DigestRandomGenerator {

        private static final long CYCLE_COUNT = 10;

        private long stateCounter;
        private long seedCounter;
        private final MessageDigest digest;
        private byte[] state;
        private byte[] seed;

        private DigestRandomGenerator(MessageDigest digest) {
            this.digest = digest;

            this.seed = new byte[digest.getDigestLength()];
            this.seedCounter = 1;

            this.state = new byte[digest.getDigestLength()];
            this.stateCounter = 1;
        }

        int getDigestLength() { return digest.getDigestLength(); }

        // NOTE: requires external synchronization
        final void addSeedMaterial(byte[] inSeed) {
            //synchronized (this) {
            digestUpdate(inSeed);
            digestUpdate(seed);
            seed = digest.digest(); // digestDoFinal(seed);
            //}
        }

        // NOTE: requires external synchronization
        final void addSeedMaterial(long rSeed) {
            //synchronized (this) {
            digestAddCounter(rSeed);
            digestUpdate(seed);
            seed = digest.digest(); // digestDoFinal(seed);
            //}
        }

        void nextBytes(byte[] bytes) {
            nextBytes(bytes, 0, bytes.length);
        }

        // NOTE: requires external synchronization
        final void nextBytes(byte[] bytes, int start, int len) {
            //synchronized (this) {
            int stateOff = 0;

            generateState();

            int end = start + len;
            for (int i = start; i != end; i++) {
                if (stateOff == state.length) {
                    generateState();
                    stateOff = 0;
                }
                bytes[i] = state[stateOff++];
            }
            //}
        }

        private void cycleSeed() {
            digestUpdate(seed);
            digestAddCounter(seedCounter++);

            seed = digest.digest(); // digestDoFinal(seed);
        }

        private void generateState() {
            digestAddCounter(stateCounter++);
            digestUpdate(state);
            digestUpdate(seed);

            state = digest.digest(); // digestDoFinal(state);

            if ((stateCounter % CYCLE_COUNT) == 0) {
                cycleSeed();
            }
        }

        private void digestAddCounter(long seed) {
            for (int i = 0; i != 8; i++) {
                digest.update((byte) seed);
                seed >>>= 8;
            }
        }

        private void digestUpdate(byte[] inSeed) {
            digest.update(inSeed, 0, inSeed.length);
        }

        //private void digestDoFinal(byte[] result) {
        //    digest.doFinal(result, 0);
        //}

    }

}
