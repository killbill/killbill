/*
 * Copyright 2014 The Billing Project, Inc.
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

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Block;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.util.config.catalog.ValidatingConfig;
import org.killbill.billing.util.config.catalog.ValidationError;
import org.killbill.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultUsage extends ValidatingConfig<StandaloneCatalog> implements Usage {

    @XmlAttribute(required = true)
    private BillingMode billingMode;

    @XmlAttribute(required = true)
    private UsageType usageType;

    @XmlElement(required = true)
    private BillingPeriod billingPeriod;

    // Used for when billing usage IN_ADVANCE & CAPACITY
    @XmlElementWrapper(name = "limits", required = false)
    @XmlElement(name = "limit", required = true)
    private DefaultLimit[] limits = new DefaultLimit[0];

    // Used for when billing usage IN_ADVANCE & CONSUMABLE
    @XmlElementWrapper(name = "blocks", required = false)
    @XmlElement(name = "block", required = true)
    private DefaultBlock[] blocks = new DefaultBlock[0];

    // Used for when billing usage IN_ARREAR
    @XmlElementWrapper(name = "tiers", required = false)
    @XmlElement(name = "tier", required = true)
    private DefaultTier[] tiers = new DefaultTier[0];

    // Used to define a fixed price for the whole usage section -- bundle several limits/blocks of units.
    @XmlElement(required = false)
    private DefaultInternationalPrice fixedPrice;

    // Used to define a recurring price for the whole usage section -- bundle several limits/blocks of units.
    @XmlElement(required = false)
    private DefaultInternationalPrice recurringPrice;


    // Not exposed in xml.
    private PlanPhase phase;

    @Override
    public BillingMode getBillingMode() {
        return billingMode;
    }

    @Override
    public UsageType getUsageType() {
        return usageType;
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    @Override
    public boolean compliesWithLimits(final String unit, final double value) {
        final Limit limit = findLimit(unit);
        if (limit != null && !limit.compliesWith(value)) {
            return false;
        }
        return true;
    }

    @Override
    public Limit[] getLimits() {
        return limits;
    }

    @Override
    public Tier[] getTiers() {
        return tiers;
    }

    @Override
    public Block[] getBlocks() {
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

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        if (billingMode == BillingMode.IN_ADVANCE && usageType == UsageType.CAPACITY && limits.length == 0) {
            errors.add(new ValidationError(String.format("Usage [IN_ADVANCE CAPACITY] section of phase %s needs to define some limits",
                                                         phase.toString()), catalog.getCatalogURI(), DefaultUsage.class, ""));
        }
        if (billingMode == BillingMode.IN_ADVANCE && usageType == UsageType.CONSUMABLE && blocks.length == 0) {
            errors.add(new ValidationError(String.format("Usage [IN_ADVANCE CONSUMABLE] section of phase %s needs to define some blocks",
                                                         phase.toString()), catalog.getCatalogURI(), DefaultUsage.class, ""));
        }

        if (billingMode == BillingMode.IN_ARREAR && tiers.length == 0) {
            errors.add(new ValidationError(String.format("Usage [IN_ARREAR] section of phase %s needs to define some tiers",
                                                         phase.toString()), catalog.getCatalogURI(), DefaultUsage.class, ""));
        }
        validateCollection(catalog, errors, limits);
        validateCollection(catalog, errors, tiers);
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog root, final URI uri) {
        for (DefaultLimit limit : limits) {
            limit.initialize(root, uri);
            limit.setUsage(this);
        }
        for (DefaultBlock block : blocks) {
            block.initialize(root, uri);
            block.setUsage(this);
        }

        for (DefaultTier tier : tiers) {
            tier.initialize(root, uri);
            tier.setUsage(this);
        }
    }

    public PlanPhase getPhase() {
        return phase;
    }

    public DefaultUsage setBillingPeriod(final BillingPeriod billingPeriod) {
        this.billingPeriod = billingPeriod;
        return this;
    }

    public DefaultUsage setBillingMode(final BillingMode billingMode) {
        this.billingMode = billingMode;
        return this;
    }

    public DefaultUsage setUsageType(final UsageType usageType) {
        this.usageType = usageType;
        return this;
    }

    public DefaultUsage setPhase(final PlanPhase phase) {
        this.phase = phase;
        return this;
    }

    public DefaultUsage setTiers(final DefaultTier[] tiers) {
        this.tiers = tiers;
        return this;
    }


    public DefaultUsage setBlocks(final DefaultBlock[] blocks) {
        this.blocks = blocks;
        return this;
    }

    public DefaultUsage setLimits(final DefaultLimit[] limits) {
        this.limits = limits;
        return this;
    }

    public DefaultUsage setFixedPrice(final DefaultInternationalPrice fixedPrice) {
        this.fixedPrice = fixedPrice;
        return this;
    }

    public DefaultUsage setRecurringPrice(final DefaultInternationalPrice recurringPrice) {
        this.recurringPrice = recurringPrice;
        return this;
    }

    protected Limit findLimit(String unit) {
        for (Limit limit : limits) {
            if (limit.getUnit().getName().equals(unit)) {
                return limit;
            }
        }
        return null;
    }
}
