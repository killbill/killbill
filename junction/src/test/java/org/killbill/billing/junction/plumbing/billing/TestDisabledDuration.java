/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.junction.plumbing.billing;

import org.joda.time.DateTime;
import org.killbill.billing.junction.JunctionTestSuiteNoDB;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestDisabledDuration extends JunctionTestSuiteNoDB {

    @Test(groups = "fast")
    public void testCompare0() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusHours(10));
        final DisabledDuration d2 = new DisabledDuration(now, now.plusHours(10));
        assertEquals(d1.compareTo(d2), 0);
        assertTrue(d1.equals(d2));
    }

    @Test(groups = "fast")
    public void testCompare1() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusHours(10));
        final DisabledDuration d2 = new DisabledDuration(now.plusSeconds(1), now.plusHours(10));
        assertEquals(d1.compareTo(d2), -1);
        assertEquals(d2.compareTo(d1), 1);
    }

    @Test(groups = "fast")
    public void testCompare2() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusHours(10));
        final DisabledDuration d2 = new DisabledDuration(now.plusSeconds(1), now.plusHours(10));
        assertEquals(d1.compareTo(d2), -1);
        assertEquals(d2.compareTo(d1), 1);
    }

    @Test(groups = "fast")
    public void testCompare3() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(1));
        final DisabledDuration d2 = new DisabledDuration(now, now.plusDays(2));
        assertEquals(d1.compareTo(d2), -1);
        assertEquals(d2.compareTo(d1), 1);
    }

    @Test(groups = "fast")
    public void testCompare4() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(1));
        final DisabledDuration d2 = new DisabledDuration(now, null);
        assertEquals(d1.compareTo(d2), -1);
        assertEquals(d2.compareTo(d1), 1);
    }

    @Test(groups = "fast")
    public void testCompare5() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, null);
        final DisabledDuration d2 = new DisabledDuration(now, now.plusDays(1));
        assertEquals(d1.compareTo(d2), 1);
        assertEquals(d2.compareTo(d1), -1);
    }



    // Case 1: this contained into o => false
    // |---------|       this
    // |--------------|  o
    @Test(groups = "fast")
    public void testDisjoint1() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(1));
        final DisabledDuration d2 = new DisabledDuration(now, now.plusDays(2));
        assertFalse(d1.isDisjoint(d2));
    }

    // Case 2: this overlaps with o => false
    // |---------|            this
    //      |--------------|  o
    @Test(groups = "fast")
    public void testDisjoint2() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(10));
        final DisabledDuration d2 = new DisabledDuration(now.plusDays(1), now.plusDays(12));
        assertFalse(d1.isDisjoint(d2));
    }

    // Case 3: o contains into this => false
    // |---------| this
    //      |---|  o
    @Test(groups = "fast")
    public void testDisjoint3() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(10));
        final DisabledDuration d2 = new DisabledDuration(now.plusDays(1), now.plusDays(4));
        assertFalse(d1.isDisjoint(d2));
    }

    // Case 4: this and o are adjacent => false
    // |---------| this
    //           |---|  o
    @Test(groups = "fast")
    public void testDisjoint4() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(10));
        final DisabledDuration d2 = new DisabledDuration(now.plusDays(10), now.plusDays(15));
        assertFalse(d1.isDisjoint(d2));
    }

    // Case 5: this and o are disjoint => true
    // |---------| this
    //             |---|  o
    @Test(groups = "fast")
    public void testDisjoint5() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(10));
        final DisabledDuration d2 = new DisabledDuration(now.plusDays(11), now.plusDays(15));
        assertTrue(d1.isDisjoint(d2));
    }


    @Test(groups = "fast")
    public void testMergeDuration1() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(10));
        final DisabledDuration d2 = new DisabledDuration(now.plusDays(1), now.plusDays(15));

        final DisabledDuration result = DisabledDuration.mergeDuration(d1, d2);
        assertEquals(result.getStart().compareTo(now), 0);
        assertEquals(result.getEnd().compareTo(now.plusDays(15)), 0);
    }

    @Test(groups = "fast")
    public void testMergeDuration2() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(15));
        final DisabledDuration d2 = new DisabledDuration(now.plusDays(1), now.plusDays(10));

        final DisabledDuration result = DisabledDuration.mergeDuration(d1, d2);
        assertEquals(result.getStart().compareTo(now), 0);
        assertEquals(result.getEnd().compareTo(now.plusDays(15)), 0);
    }

    @Test(groups = "fast")
    public void testMergeDuration3() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, null);
        final DisabledDuration d2 = new DisabledDuration(now.plusDays(1), now.plusDays(10));

        final DisabledDuration result = DisabledDuration.mergeDuration(d1, d2);
        assertEquals(result.getStart().compareTo(now), 0);
        assertNull(result.getEnd());
    }

    @Test(groups = "fast")
    public void testMergeDuration4() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(10));
        final DisabledDuration d2 = new DisabledDuration(now.plusDays(1), null);

        final DisabledDuration result = DisabledDuration.mergeDuration(d1, d2);
        assertEquals(result.getStart().compareTo(now), 0);
        assertNull(result.getEnd());
    }

    @Test(groups = "fast", expectedExceptions = IllegalStateException.class)
    public void testMergeDurationInvalid1() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now, now.plusDays(10));
        final DisabledDuration d2 = new DisabledDuration(now.plusDays(11), null);

        DisabledDuration.mergeDuration(d1, d2);
    }

    @Test(groups = "fast", expectedExceptions = IllegalStateException.class)
    public void testMergeDurationInvalid2() throws Exception {
        final DateTime now = clock.getUTCNow();
        final DisabledDuration d1 = new DisabledDuration(now.plusDays(1), now.plusDays(10));
        final DisabledDuration d2 = new DisabledDuration(now, null);

        DisabledDuration.mergeDuration(d1, d2);
    }

}