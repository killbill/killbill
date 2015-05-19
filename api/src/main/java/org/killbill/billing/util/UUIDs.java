/*
 * Copyright 2015 The Billing Project, LLC
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

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDs helper.
 *
 * @author kares
 */
public abstract class UUIDs {

    public static UUID randomUUID() { return rndUUIDv4(); }

    private static UUID rndUUIDv4() {
        // ~ return UUID.randomUUID() :
        final SecureRandom random = threadRandom.get();

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

    private static final ThreadLocal<SecureRandom> threadRandom =
        new ThreadLocal<SecureRandom>() {
            protected SecureRandom initialValue() {
                return new SecureRandom();
            }
    };

}
