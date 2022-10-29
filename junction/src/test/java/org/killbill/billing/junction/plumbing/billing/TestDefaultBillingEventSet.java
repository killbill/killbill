/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.JunctionTestSuiteNoDB;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultBillingEventSet extends JunctionTestSuiteNoDB {

    /**
     * a different usage name needed so when {@link Usage} added to {@link Map} in
     * {@link DefaultBillingEventSet#getUsages()} implementation, the {@link Map} will contain different key.
     */
    private Usage mockUsage(final String name) {
        final Usage usage = Mockito.mock(Usage.class);
        Mockito.when(usage.getName()).thenReturn(name);
        return usage;
    }

    private BillingEvent createDefaultBillingEvent(final String usageName) {
        try {
            final List<Usage> usages = List.of(mockUsage(usageName));
            final BillingEvent billingEvent = createEvent(subscription(UUID.randomUUID()), DateTime.now(), SubscriptionBaseTransitionType.CREATE);
            final BillingEvent spied = Mockito.spy(billingEvent);
            Mockito.when(spied.getUsages()).thenReturn(usages);
            return spied;
        } catch (final CatalogApiException e) {
            throw new RuntimeException("There's problem in test structure if this is happened.");
        }
    }

    @Test(groups = "fast")
    public void getUsages() {
        final BillingEvent e1 = createDefaultBillingEvent("A");
        final BillingEvent e2 = createDefaultBillingEvent("B");
        final BillingEvent e3 = createDefaultBillingEvent("C");

        final BillingEventSet billingEventSet = new DefaultBillingEventSet(false, false, false);
        billingEventSet.addAll(List.of(e1, e2, e3));

        final Map<String, Usage> usages = billingEventSet.getUsages();

        Assert.assertNotNull(usages);
        Assert.assertEquals(usages.size(), 3);
    }

    @Test(groups = "fast")
    public void getUsagesWithCatalogApiException() {
        final BillingEvent e1 = createDefaultBillingEvent("A");
        final BillingEvent e2 = createDefaultBillingEvent("B");
        final BillingEvent e3 = createDefaultBillingEvent("C");
        try {
            Mockito.doAnswer(invocationOnMock -> { throw new CatalogApiException(ErrorCode.__UNKNOWN_ERROR_CODE); }).when(e3).getUsages();
        } catch (final CatalogApiException e) {
            throw new RuntimeException("There's problem in test structure if this is happened.");
        }

        final BillingEventSet billingEventSet = new DefaultBillingEventSet(false, false, false);
        billingEventSet.addAll(List.of(e1, e2, e3));

        try {
            billingEventSet.getUsages();
            Assert.fail("Error because in this test, e3.getUsages() modified to throw an exception");
        } catch (final IllegalStateException e) {
            Assert.assertNotNull(e.getMessage());
            Assert.assertTrue(e.getMessage().contains("Failed to retrieve usage section for billing event"));
        }
    }
}
