/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.net.URI;
import java.util.Arrays;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;



@XmlAccessorType(XmlAccessType.NONE)
public class DefaultTier extends ValidatingConfig<StandaloneCatalog> implements Tier {

    @XmlElementWrapper(name = "limits", required = false)
    @XmlElement(name = "limit", required = false)
    private DefaultLimit[] limits;

    @XmlElementWrapper(name = "blocks", required = false)
    @XmlElement(name = "tieredBlock", required = false)
    private DefaultTieredBlock[] blocks;

    // Used to define a fixed price for the whole tier section
    @XmlElement(required = false)
    private DefaultInternationalPrice fixedPrice;

    // Used to define a recurring price for the whole tier section
    @XmlElement(required = false)
    private DefaultInternationalPrice recurringPrice;

    // Not defined in catalog
    private BillingMode billingMode;
    private UsageType usageType;
    private PlanPhase phase;

    public DefaultTier() {
        limits = new DefaultLimit[0];
        blocks = new DefaultTieredBlock[0];
    }

    public DefaultTier(Tier in, TierPriceOverride override, Currency currency) {
        this.limits = (DefaultLimit[])in.getLimits();
        this.blocks = new DefaultTieredBlock[in.getTieredBlocks().length];

        for (int i = 0; i < in.getTieredBlocks().length; i++) {
            if(override != null && override.getTieredBlockPriceOverrides() != null) {
                final TieredBlock curTieredBlock = in.getTieredBlocks()[i];
                final TieredBlockPriceOverride overriddenTierBlock = Iterables.tryFind(override.getTieredBlockPriceOverrides(), new Predicate<TieredBlockPriceOverride>() {
                    @Override
                    public boolean apply(final TieredBlockPriceOverride input) {
                        return (input != null && input.getUnitName().equals(curTieredBlock.getUnit().getName()) &&
                                Double.compare(input.getSize(), curTieredBlock.getSize()) == 0 &&
                                Double.compare(input.getMax(), curTieredBlock.getMax()) == 0);
                    }

                }).orNull();
                blocks[i] = (overriddenTierBlock != null) ? new DefaultTieredBlock(in.getTieredBlocks()[i], overriddenTierBlock, currency) :
                        (DefaultTieredBlock) in.getTieredBlocks()[i];
            }
            else {
                blocks[i] = (DefaultTieredBlock) in.getTieredBlocks()[i];
            }
        }
    }

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

    public void setBillingMode(final BillingMode billingMode) {
        this.billingMode = billingMode;
    }

    public void setUsageType(final UsageType usageType) {
        this.usageType = usageType;
    }

    public DefaultTier setPhase(final PlanPhase phase) {
        this.phase = phase;
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
        if (billingMode == BillingMode.IN_ARREAR && usageType == UsageType.CAPACITY && limits.length == 0) {
            errors.add(new ValidationError(String.format("Usage [IN_ARREAR CAPACITY] section of phase %s needs to define some limits",
                                                         phase.getName()), catalog.getCatalogURI(), DefaultUsage.class, ""));
        }
        if (billingMode == BillingMode.IN_ARREAR && usageType == UsageType.CONSUMABLE && blocks.length == 0) {
            errors.add(new ValidationError(String.format("Usage [IN_ARREAR CONSUMABLE] section of phase %s needs to define some blocks",
                                                         phase.getName()), catalog.getCatalogURI(), DefaultUsage.class, ""));
        }
        validateCollection(catalog, errors, limits);
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
        for (DefaultLimit cur : limits) {
            cur.initialize(catalog, sourceURI);
        }
        for (DefaultBlock cur : blocks) {
            cur.initialize(catalog, sourceURI);
        }
    }



    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultTier)) {
            return false;
        }

        final DefaultTier that = (DefaultTier) o;

        if (billingMode != that.billingMode) {
            return false;
        }
        if (!Arrays.equals(blocks, that.blocks)) {
            return false;
        }
        if (fixedPrice != null ? !fixedPrice.equals(that.fixedPrice) : that.fixedPrice != null) {
            return false;
        }
        if (!Arrays.equals(limits, that.limits)) {
            return false;
        }
        if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
            return false;
        }
        if (recurringPrice != null ? !recurringPrice.equals(that.recurringPrice) : that.recurringPrice != null) {
            return false;
        }
        if (usageType != that.usageType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = limits != null ? Arrays.hashCode(limits) : 0;
        result = 31 * result + (blocks != null ? Arrays.hashCode(blocks) : 0);
        result = 31 * result + (fixedPrice != null ? fixedPrice.hashCode() : 0);
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        return result;
    }
}
