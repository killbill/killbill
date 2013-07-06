/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.InvoiceTestSuiteNoDB;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.events.NullInvoiceInternalEvent;
import com.ning.billing.util.jackson.ObjectMapper;

public class TestEventJson extends InvoiceTestSuiteNoDB {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testInvoiceCreationEvent() throws Exception {
        final InvoiceCreationInternalEvent e = new DefaultInvoiceCreationEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(12.0), Currency.USD);
        final String json = mapper.writeValueAsString(e);

        final Object obj = mapper.readValue(json, DefaultInvoiceCreationEvent.class);
        Assert.assertEquals(obj, e);
    }

    @Test(groups = "fast")
    public void testEmptyInvoiceEvent() throws Exception {
        final NullInvoiceInternalEvent e = new DefaultNullInvoiceEvent(UUID.randomUUID(), new LocalDate());
        final String json = mapper.writeValueAsString(e);

        final Object obj = mapper.readValue(json, DefaultNullInvoiceEvent.class);
        Assert.assertEquals(obj, e);
    }
}
