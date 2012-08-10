/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultAuditLogsForInvoices extends AuditLogsTestBase {

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final Map<UUID, List<AuditLog>> invoicesAuditLogs = createAuditLogsAssociation();
        final Map<UUID, List<AuditLog>> invoiceItemsAuditLogs = createAuditLogsAssociation();
        Assert.assertEquals(new DefaultAuditLogsForInvoices(invoicesAuditLogs, invoiceItemsAuditLogs).getInvoiceAuditLogs(), invoicesAuditLogs);
        Assert.assertEquals(new DefaultAuditLogsForInvoices(invoicesAuditLogs, invoiceItemsAuditLogs).getInvoiceItemsAuditLogs(), invoiceItemsAuditLogs);

        Assert.assertNotEquals(new DefaultAuditLogsForInvoices(createAuditLogsAssociation(), invoiceItemsAuditLogs).getInvoiceAuditLogs(), invoicesAuditLogs);
        Assert.assertEquals(new DefaultAuditLogsForInvoices(createAuditLogsAssociation(), invoiceItemsAuditLogs).getInvoiceItemsAuditLogs(), invoiceItemsAuditLogs);
        Assert.assertEquals(new DefaultAuditLogsForInvoices(invoicesAuditLogs, createAuditLogsAssociation()).getInvoiceAuditLogs(), invoicesAuditLogs);
        Assert.assertNotEquals(new DefaultAuditLogsForInvoices(invoicesAuditLogs, createAuditLogsAssociation()).getInvoiceItemsAuditLogs(), invoiceItemsAuditLogs);
        Assert.assertNotEquals(new DefaultAuditLogsForInvoices(createAuditLogsAssociation(), createAuditLogsAssociation()).getInvoiceAuditLogs(), invoicesAuditLogs);
        Assert.assertNotEquals(new DefaultAuditLogsForInvoices(createAuditLogsAssociation(), createAuditLogsAssociation()).getInvoiceItemsAuditLogs(), invoiceItemsAuditLogs);
    }
}
