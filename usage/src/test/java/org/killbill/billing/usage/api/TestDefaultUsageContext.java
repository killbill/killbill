/*
 * Copyright 2014-2026 The Billing Project, LLC
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

package org.killbill.billing.usage.api;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.util.callcontext.TenantContext;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultUsageContext {

    private final TenantContext mockContext = Mockito.mock(TenantContext.class);

    @Test(groups = "fast")
    public void testGetUsageTransitionsReturnsNullByDefault() {
        final DefaultUsageContext ctx = new DefaultUsageContext(null, null, mockContext);
        Assert.assertNull(ctx.getUsageTransitions(),
                          "Single-argument constructor must expose null transitions for backwards compatibility");
    }

    @Test(groups = "fast")
    public void testGetUsageTransitionsReturnsProvidedMap() {
        final UUID subscriptionId = UUID.randomUUID();
        final Map.Entry<UUID, String> key = new AbstractMap.SimpleEntry<>(subscriptionId, "UNIT_TYPE");
        final Set<DateTime> times = Collections.singleton(new DateTime(2026, 1, 1, 0, 0));
        final Map<Map.Entry<UUID, String>, Set<DateTime>> transitions = Collections.singletonMap(key, times);

        final DefaultUsageContext ctx = new DefaultUsageContext(DryRunType.UPCOMING_INVOICE, new LocalDate(2026, 1, 31), mockContext, transitions);

        Assert.assertEquals(ctx.getUsageTransitions(), transitions,
                            "getUsageTransitions() must return the map passed to the constructor");
    }

    @Test(groups = "fast")
    public void testGetUsageTransitionsNullExplicitlyPassed() {
        final DefaultUsageContext ctx = new DefaultUsageContext(null, null, mockContext, null);
        Assert.assertNull(ctx.getUsageTransitions(),
                          "Explicitly passing null transitions must return null");
    }

    @Test(groups = "fast")
    public void testOtherFieldsUnaffectedByTransitions() {
        final LocalDate targetDate = new LocalDate(2026, 6, 15);
        final UUID tenantId = UUID.randomUUID();
        Mockito.when(mockContext.getTenantId()).thenReturn(tenantId);

        final DefaultUsageContext ctx = new DefaultUsageContext(DryRunType.SUBSCRIPTION_ACTION, targetDate, mockContext, Collections.emptyMap());

        Assert.assertEquals(ctx.getDryRunType(), DryRunType.SUBSCRIPTION_ACTION);
        Assert.assertEquals(ctx.getInputTargetDate(), targetDate);
        Assert.assertEquals(ctx.getTenantId(), tenantId);
    }
}
