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

    @Test(groups = "fast")
    public void testBuildMarkerName() {
        final String input = "org.killbill.billing.invoice.generator.DefaultInvoiceGenerator";

        final String res = ThreadNameBasedDiscriminator.buildMarkerName("org.killbill.billing.", input.toCharArray(), 21);
        Assert.assertEquals(res, "org.killbill.billing.invoice");
    }

    @Test(groups = "fast")
    public void testBuildMarkerNameWithoutNextSeparator() {
        final String input = "org.killbill.commons.metrics";

        final String res = ThreadNameBasedDiscriminator.buildMarkerName("org.killbill.", input.toCharArray(), 13);
        Assert.assertEquals(res, "org.killbill.commons");
    }

    @Test(groups = "fast")
    public void testBuildPluginMarkerName() {
        final String input = "org.killbill.billing.plugin.analytics.segment.SegmentPluginApi.";

        final String res = ThreadNameBasedDiscriminator.buildPluginMarkerName(input.toCharArray(), 28);
        Assert.assertEquals(res, "SegmentPluginApi");
    }

    @Test(groups = "fast")
    public void testBuildPluginMarkerNameReturnsNullWhenNoTokenTerminatorExists() {
        final String input = "NoTokenTerminatorApi";

        final String res = ThreadNameBasedDiscriminator.buildPluginMarkerName(input.toCharArray(), 0);
        Assert.assertNull(res);
    }

    @Test(groups = "fast")
    public void testExtractMarkerForKillbillBilling() {
        Assert.assertEquals(
                ThreadNameBasedDiscriminator.extractMarker("org.killbill.billing.invoice.generator.DefaultInvoiceGenerator"),
                "org.killbill.billing.invoice");
    }

    @Test(groups = "fast")
    public void testExtractMarkerForKillbillCommons() {
        Assert.assertEquals(
                ThreadNameBasedDiscriminator.extractMarker("org.killbill.commons.metrics.api.MetricRegistry"),
                "org.killbill.commons.metrics");
    }

    @Test(groups = "fast")
    public void testExtractMarkerForOtherKillbillTopLevelPackage() {
        Assert.assertEquals(
                ThreadNameBasedDiscriminator.extractMarker("org.killbill.notificationq.DefaultNotificationQueueService"),
                "org.killbill.notificationq");
    }

    @Test(groups = "fast")
    public void testExtractMarkerSkipsEntityWrappers() {
        Assert.assertNull(ThreadNameBasedDiscriminator.extractMarker("org.killbill.billing.util.entity.dao.EntitySqlDao"));
    }

    @Test(groups = "fast")
    public void testExtractMarkerSkipsDaoWrappers() {
        Assert.assertNull(ThreadNameBasedDiscriminator.extractMarker("org.killbill.billing.util.dao.NonEntityDao"));
    }

    @Test(groups = "fast")
    public void testExtractMarkerSkipsProfilingWrappers() {
        Assert.assertNull(ThreadNameBasedDiscriminator.extractMarker("org.killbill.commons.profiling.Profiling"));
    }

    @Test(groups = "fast")
    public void testExtractMarkerSkipsJdbiWrappers() {
        Assert.assertNull(ThreadNameBasedDiscriminator.extractMarker("org.killbill.commons.jdbi.statement.SmartFetchSize"));
    }

    @Test(groups = "fast")
    public void testExtractMarkerForKillbillBillingPluginUsesNormalRule() {
        // org.killbill.billing.plugin.* is matched by the org.killbill.billing.* rule
        // (the dedicated plugin marker handling only applies to com.killbill.billing.plugin.*).
        Assert.assertEquals(
                ThreadNameBasedDiscriminator.extractMarker("org.killbill.billing.plugin.analytics.segment.SegmentPluginApi"),
                "org.killbill.billing.plugin");
    }

    @Test(groups = "fast")
    public void testExtractMarkerForComKillbillBillingPlugin() {
        // buildPluginMarkerName returns the last dot-terminated token;
        // for a class whose simple name is the trailing identifier (no trailing '.'),
        // the marker is therefore the last sub-package, not the simple class name.
        Assert.assertEquals(
                ThreadNameBasedDiscriminator.extractMarker("com.killbill.billing.plugin.analytics.segment.SegmentPluginApi"),
                "segment");
    }

    @Test(groups = "fast")
    public void testExtractMarkerReturnsNullForUnknownPackages() {
        Assert.assertNull(ThreadNameBasedDiscriminator.extractMarker("ch.qos.logback.classic.Logger"));
        Assert.assertNull(ThreadNameBasedDiscriminator.extractMarker("java.lang.Thread"));
        Assert.assertNull(ThreadNameBasedDiscriminator.extractMarker(""));
        Assert.assertNull(ThreadNameBasedDiscriminator.extractMarker(null));
    }

    @Test(groups = "fast")
    public void testGetDiscriminatingValueWalksStackAndDetectsCaller() {
        // Invoking the discriminator from this test class (org.killbill.billing.server.log.*)
        // must produce the org.killbill.billing.server marker via the StackWalker, and
        // must not return the discriminator's own class as the marker.
        final ThreadNameBasedDiscriminator discriminator = new ThreadNameBasedDiscriminator();
        final String value = discriminator.getDiscriminatingValue(null);
        Assert.assertEquals(value, "org.killbill.billing.server");
    }
}
