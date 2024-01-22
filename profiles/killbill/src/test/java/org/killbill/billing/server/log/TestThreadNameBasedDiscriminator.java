/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.server.log;

import org.killbill.billing.KillbillTestSuite;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestThreadNameBasedDiscriminator extends KillbillTestSuite {

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
    }

    @Test(groups = "fast")
    public void testLookupNextToken() {
        final String input = "org.killbill.billing.dao.foo.";

        int next = ThreadNameBasedDiscriminator.lookupNextToken(input.toCharArray(), 0, "org.");
        Assert.assertEquals(next, 4);

        next = ThreadNameBasedDiscriminator.lookupNextToken(input.toCharArray(), next, "killbill.");
        Assert.assertEquals(next, 13);

        int nextInvalid = ThreadNameBasedDiscriminator.lookupNextToken(input.toCharArray(), next, "zilling.");
        Assert.assertEquals(nextInvalid, -1);

        next = ThreadNameBasedDiscriminator.lookupNextToken(input.toCharArray(), next, "billing.");
        Assert.assertEquals(next, 21);

        next = ThreadNameBasedDiscriminator.lookupNextToken(input.toCharArray(), next, "dao.");
        Assert.assertEquals(next, 25);

        next = ThreadNameBasedDiscriminator.lookupNextToken(input.toCharArray(), next, "foo.");
        Assert.assertEquals(next, 29);

        nextInvalid = ThreadNameBasedDiscriminator.lookupNextToken(input.toCharArray(), next, "end");
        Assert.assertEquals(nextInvalid, -1);
    }

    @Test(groups = "fast")
    public void testFindNextToken() {

        final String input = "org.killbill.billing.dao.foo.";
        String res = ThreadNameBasedDiscriminator.findNextToken(input.toCharArray(), 0, '.');
        Assert.assertEquals(res, "org.");

        int next = res.length();
        res = ThreadNameBasedDiscriminator.findNextToken(input.toCharArray(), next, '.');
        Assert.assertEquals(res, "killbill.");

        next += res.length();
        res = ThreadNameBasedDiscriminator.findNextToken(input.toCharArray(), next, '.');
        Assert.assertEquals(res, "billing.");

        next += res.length();
        res = ThreadNameBasedDiscriminator.findNextToken(input.toCharArray(), next, '.');
        Assert.assertEquals(res, "dao.");

        next += res.length();
        res = ThreadNameBasedDiscriminator.findNextToken(input.toCharArray(), next, '.');
        Assert.assertEquals(res, "foo.");

        next += res.length();
        res = ThreadNameBasedDiscriminator.findNextToken(input.toCharArray(), next, '.');
        Assert.assertNull(res);
    }
}