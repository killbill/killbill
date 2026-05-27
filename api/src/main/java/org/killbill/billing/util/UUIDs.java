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
 * Identifier generation defaults to UUIDv7 (RFC 9562): a 48-bit Unix
 * millisecond timestamp followed by version/variant tag bits and random
 * payload. Within a JVM, identifiers minted by {@link #randomUUID()} are
 * strictly monotonically increasing — including across calls that fall in
 * the same millisecond — which makes them friendly to B-tree primary keys
 * in databases that recognise UUIDv7 (PostgreSQL 18+, etc.).
 *
 * The legacy UUIDv4 generator is retained as {@link #randomUUIDv4()} for
 * callers that need a fully random identifier with no temporal correlation.
 *
 * @author kares
 */
public abstract class UUIDs {

    public static UUID randomUUID() { return rndUUIDv7(); }

    /**
     * @return a freshly generated UUIDv4 (fully random, no temporal ordering).
     */
    public static UUID randomUUIDv4() { return rndUUIDv4(); }

    /**
     * @return a freshly generated UUIDv7 (time-ordered, RFC 9562). Equivalent
     *         to {@link #randomUUID()} but explicit about the version.
     */
    public static UUID randomUUIDv7() { return rndUUIDv7(); }

    public static void setRandom(final Random random) {
        threadRandom.set(random);
    }

    public static Random getRandom() {
        return threadRandom.get();
    }

    // Guards the (lastMillis, sequence) pair used to keep UUIDv7 outputs
    // monotonic when many identifiers are requested within the same ms.
    private static final Object v7Lock = new Object();
    private static long v7LastMillis = -1L;
    private static int v7Sequence = 0;
    // rand_a is 12 bits in the UUIDv7 layout; the counter occupies the same
    // field, so it must never exceed 0xFFF.
    private static final int V7_SEQUENCE_MAX = 0x0FFF;

    private static UUID rndUUIDv7() {
        final long timestamp;
        final int sequence;
        synchronized (v7Lock) {
            long now = System.currentTimeMillis();
            if (now > v7LastMillis) {
                v7LastMillis = now;
                v7Sequence = 0;
            } else {
                // Same millisecond (or, very rarely, a backwards clock step).
                // Bump the sequence; if it overflows the 12-bit field, borrow
                // a millisecond from the future so we never break ordering.
                if (v7Sequence >= V7_SEQUENCE_MAX) {
                    v7LastMillis += 1L;
                    v7Sequence = 0;
                } else {
                    v7Sequence++;
                }
            }
            timestamp = v7LastMillis;
            sequence = v7Sequence;
        }

        final Random random = threadRandom.get();
        final byte[] randB = new byte[8];
        random.nextBytes(randB);

        // High 64 bits: 48-bit ms timestamp | 4-bit version (0x7) | 12-bit sequence
        long msb = (timestamp & 0x0000FFFFFFFFFFFFL) << 16;
        msb |= 0x7000L;                       // version 7
        msb |= (sequence & 0x0FFFL);          // rand_a (sub-ms counter)

        // Low 64 bits: 2-bit variant (0b10) | 62 bits of randomness
        long lsb = 0L;
        for (int i = 0; i < 8; i++) {
            lsb = (lsb << 8) | (randB[i] & 0xffL);
        }
        lsb &= 0x3FFFFFFFFFFFFFFFL;           // clear top two bits
        lsb |= 0x8000000000000000L;           // set IETF variant (0b10xx...)

        return new UUID(msb, lsb);
    }

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
