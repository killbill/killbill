/*
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.catalog;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.Block;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultTier extends ValidatingConfig<StandaloneCatalog> implements Tier {

    @XmlElementWrapper(name = "limits", required = false)
    @XmlElement(name = "limit", required = true)
    private DefaultLimit[] limits = new DefaultLimit[0];

    @XmlElementWrapper(name = "blocks", required = false)
    @XmlElement(name = "tieredBlock", required = true)
    private DefaultTieredBlock[] blocks = new DefaultTieredBlock[0];

    // Used to define a fixed price for the whole tier section
    @XmlElement(required = false)
    private DefaultInternationalPrice fixedPrice;

    // Used to define a recurring price for the whole tier section
    @XmlElement(required = false)
    private DefaultInternationalPrice recurringPrice;


    // Not defined in catalog
    private DefaultUsage usage;

    @Override
    public DefaultLimit[] getLimits() {
        return limits;
    }

    @Override
    public DefaultTieredBlock[] getTieredBlocks() {
        return blocks;
    }

    @Override
    public InternationalPrice getFixedPrice() {
        return fixedPrice;
    }

    @Override
    public InternationalPrice getRecurringPrice() {
        return recurringPrice;
    }

    public DefaultTier setLimits(final DefaultLimit[] limits) {
        this.limits = limits;
        return this;
    }

    public DefaultTier setBlocks(final DefaultTieredBlock[] blocks) {
        this.blocks = blocks;
        return this;
    }

    public DefaultTier setUsage(final DefaultUsage usage) {
        this.usage = usage;
        return this;
    }

    public DefaultTier setFixedPrice(final DefaultInternationalPrice fixedPrice) {
        this.fixedPrice = fixedPrice;
        return this;
    }

    public DefaultTier setRecurringPrice(final DefaultInternationalPrice recurringPrice) {
        this.recurringPrice = recurringPrice;
        return this;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {

        if (usage.getBillingMode() == BillingMode.IN_ARREAR && usage.getUsageType() == UsageType.CAPACITY && limits.length == 0) {
            errors.add(new ValidationError(String.format("Usage [IN_ARREAR CAPACITY] section of phase %s needs to define some limits",
                                                         usage.getPhase().toString()), catalog.getCatalogURI(), DefaultUsage.class, ""));
        }
        if (usage.getBillingMode() == BillingMode.IN_ARREAR && usage.getUsageType() == UsageType.CONSUMABLE && blocks.length == 0) {
            errors.add(new ValidationError(String.format("Usage [IN_ARREAR CONSUMABLE] section of phase %s needs to define some blocks",
                                                         usage.getPhase().toString()), catalog.getCatalogURI(), DefaultUsage.class, ""));
        }
        validateCollection(catalog, errors, limits);
        return errors;
    }
}
