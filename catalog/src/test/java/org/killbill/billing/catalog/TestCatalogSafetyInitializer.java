/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.catalog.api.FixedType;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.TimeUnit;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCatalogSafetyInitializer {

    @XmlElementWrapper(name = "initialPhasesWrapperAllRequired", required = true)
    @XmlElement(name = "phase", required = true)
    private DefaultPlanPhase[] initialPhasesWrapperAllRequired;

    @XmlElementWrapper(name = "initialPhasesWrapperNotRequired", required = false)
    @XmlElement(name = "phase", required = false)
    private DefaultPlanPhase[] initialPhasesWrapperNotRequired;

    @XmlElementWrapper(name = "initialPhasesWrapper", required = true)
    @XmlElement(name = "phase", required = false)
    private DefaultPlanPhase[] initialPhasesWrapper;

    @XmlElement(name = "pricesNotRequired", required = false)
    private DefaultPrice[] pricesNotRequired;

    @XmlElement(name = "prices", required = true)
    private DefaultPrice[] prices;

    @XmlElementWrapper(name = "available", required = false)
    @XmlIDREF
    @XmlElement(type = DefaultProduct.class, name = "addonProduct", required = false)
    private CatalogEntityCollection<Product> available;

    @XmlElement(required = true)
    private TimeUnit unit;

    @XmlElement(required = false)
    private Integer number;

    @XmlElement(required = false)
    private int smallNumber;

    @XmlAttribute(required = false)
    private FixedType type;



    @Test(groups = "fast")
    public void testNonRequiredArrayFields() {

        final TestCatalogSafetyInitializer test = new TestCatalogSafetyInitializer();
        Assert.assertNull(test.getInitialPhasesWrapperAllRequired());
        Assert.assertNull(test.getInitialPhasesWrapperNotRequired());
        Assert.assertNull(test.getInitialPhasesWrapper());
        Assert.assertNull(test.getPricesNotRequired());
        Assert.assertNull(test.getPrices());

        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(test);

        Assert.assertNull(test.getInitialPhasesWrapperAllRequired());
        Assert.assertNotNull(test.getInitialPhasesWrapperNotRequired());
        Assert.assertEquals(test.getInitialPhasesWrapperNotRequired().length, 0);
        Assert.assertNull(test.getInitialPhasesWrapper());
        Assert.assertNotNull(test.getPricesNotRequired());
        Assert.assertEquals(test.getPricesNotRequired().length, 0);
        Assert.assertNull(test.getPrices());

        Assert.assertNotNull(test.getNumber());
        Assert.assertEquals(test.getNumber(), CatalogSafetyInitializer.DEFAULT_NON_REQUIRED_INTEGER_FIELD_VALUE);

        Assert.assertNotNull(test.getSmallNumber());

        Assert.assertNotNull(test.getType());
        Assert.assertEquals(test.getType(), FixedType.ONE_TIME);
    }

    public DefaultPlanPhase[] getInitialPhasesWrapperAllRequired() {
        return initialPhasesWrapperAllRequired;
    }

    public DefaultPlanPhase[] getInitialPhasesWrapperNotRequired() {
        return initialPhasesWrapperNotRequired;
    }

    public DefaultPlanPhase[] getInitialPhasesWrapper() {
        return initialPhasesWrapper;
    }

    public DefaultPrice[] getPricesNotRequired() {
        return pricesNotRequired;
    }

    public DefaultPrice[] getPrices() {
        return prices;
    }

    public CatalogEntityCollection<Product> getAvailable() {
        return available;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public Integer getNumber() {
        return number;
    }

    public int getSmallNumber() {
        return smallNumber;
    }

    public FixedType getType() {
        return type;
    }
}
