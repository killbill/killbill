/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.catalog;

import java.io.IOException;
import java.util.Collection;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.rules.DefaultPlanRules;
import org.redisson.client.codec.Codec;
import org.redisson.codec.SerializationCodec;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.netty.buffer.ByteBuf;

public class TestVersionedCatalog extends CatalogTestSuiteNoDB {

    // WeaponsHireSmall-1.xml
    final DateTime dt1 = new DateTime("2010-01-01T00:00:00+00:00");

    private DefaultVersionedCatalog vc;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        vc = loader.loadDefaultCatalog("versionedCatalog");
    }

    @Test(groups = "fast")
    public void testErrorOnDateTooEarly() throws CatalogApiException {
        // We find it although the date provided is too early because we default to first catalog version
        vc.findPlan("shotgun-monthly", dt1);

        try {
            // We **don't find it** because date is too early and not part of first catalog version
            vc.findPlan("shotgun-quarterly", dt1);
            Assert.fail("Date is too early an exception should have been thrown");
        } catch (final CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_NO_SUCH_PLAN.getCode());
        }
    }

    @Test(groups = "fast")
    public void testDefaultPlanRulesExternalizable() throws IOException, CatalogApiException {
        final Codec codec = new SerializationCodec();
        final ByteBuf byteBuf = codec.getValueEncoder().encode(vc.getVersions().get(0).getPlanRules());
        final DefaultPlanRules planRules = (DefaultPlanRules) codec.getValueDecoder().decode(byteBuf, null);
        Assert.assertEquals(planRules, vc.getVersions().get(0).getPlanRules());
    }

    @Test(groups = "fast")
    public void testProductExternalizable() throws IOException {
        final Codec codec = new SerializationCodec();
        for (final Product product : ((StandaloneCatalog) vc.getVersions().get(0)).getCatalogEntityCollectionProduct().getEntries()) {
            final ByteBuf byteBuf = codec.getValueEncoder().encode(product);
            final Product product2 = (Product) codec.getValueDecoder().decode(byteBuf, null);
            Assert.assertEquals(product2, product);
        }
    }

    @Test(groups = "fast")
    public void testCatalogEntityCollectionProductExternalizable() throws IOException {
        final Codec codec = new SerializationCodec();
        final ByteBuf byteBuf = codec.getValueEncoder().encode(((StandaloneCatalog) vc.getVersions().get(0)).getCatalogEntityCollectionProduct());
        final Collection products = (CatalogEntityCollection) codec.getValueDecoder().decode(byteBuf, null);
        Assert.assertEquals(products, ((StandaloneCatalog) vc.getVersions().get(0)).getCatalogEntityCollectionProduct());
    }

    @Test(groups = "fast")
    public void testStandaloneCatalogExternalizable() throws IOException {
        final Codec codec = new SerializationCodec();
        final ByteBuf byteBuf = codec.getValueEncoder().encode(vc.getVersions().get(0));
        final StandaloneCatalog standaloneCatalog = (StandaloneCatalog) codec.getValueDecoder().decode(byteBuf, null);
        Assert.assertEquals(standaloneCatalog, vc.getVersions().get(0));
    }
}
