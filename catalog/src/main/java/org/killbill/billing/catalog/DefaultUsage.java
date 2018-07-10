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
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Block;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.catalog.api.TierBlockPolicy;

import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;


@XmlAccessorType(XmlAccessType.NONE)
public class DefaultUsage extends ValidatingConfig<StandaloneCatalog> implements Usage {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlAttribute(required = false)
    private String prettyName;

    @XmlAttribute(required = true)
    private BillingMode billingMode;

    @XmlAttribute(required = true)
    private UsageType usageType;

    @XmlAttribute(required = false)
    private TierBlockPolicy tierBlockPolicy;

    @XmlElement(required = true)
    private BillingPeriod billingPeriod;

    @XmlElementWrapper(name = "limits", required = false)
    @XmlElement(name = "limit", required = false)
    private DefaultLimit[] limits;

    @XmlElementWrapper(name = "blocks", required = false)
    @XmlElement(name = "block", required = false)
    private DefaultBlock[] blocks;

    @XmlElementWrapper(name = "tiers", required = false)
    @XmlElement(name = "tier", required = false)
    private DefaultTier[] tiers;

    // Used to define a fixed price for the whole usage section -- bundle several limits/blocks of units.
    @XmlElement(required = false)
    private DefaultInternationalPrice fixedPrice;

    // Used to define a recurring price for the whole usage section -- bundle several limits/blocks of units.
    @XmlElement(required = false)
    private DefaultInternationalPrice recurringPrice;

    // Not exposed in xml.
    private PlanPhase phase;

    public DefaultUsage() {
        limits = new DefaultLimit[0];
        blocks = new DefaultBlock[0];
        tiers = new DefaultTier[0];
    }

    public DefaultUsage(final Usage in, UsagePriceOverride override, Currency currency) {
              this.name = in.getName();
              this.usageType = in.getUsageType();
              this.tierBlockPolicy = in.getTierBlockPolicy();
              this.billingPeriod = in.getBillingPeriod();
              this.billingMode = in.getBillingMode();
              this.limits = (DefaultLimit[]) in.getLimits();
              this.blocks = (DefaultBlock[]) in.getBlocks();
              this.tiers = new DefaultTier[in.getTiers().length];

              for (int i = 0; i < in.getTiers().length; i++) {
                  if(override != null && override.getTierPriceOverrides()!=null) {
                      final TieredBlock[] curTieredBlocks = in.getTiers()[i].getTieredBlocks();

                      final TierPriceOverride overriddenTier = Iterables.tryFind(override.getTierPriceOverrides(), new Predicate<TierPriceOverride>() {
                          @Override
                          public boolean apply(final TierPriceOverride input) {

                            if(input !=null) {
                              final List<TieredBlockPriceOverride> blockPriceOverrides = input.getTieredBlockPriceOverrides();
                              for (TieredBlockPriceOverride blockDef : blockPriceOverrides) {
                                 String unitName = blockDef.getUnitName();
                                 Double max = blockDef.getMax();
                                 Double size = blockDef.getSize();
                                 for (TieredBlock curTieredBlock : curTieredBlocks) {
                                     if (unitName.equals(curTieredBlock.getUnit().getName()) &&
                                             Double.compare(size, curTieredBlock.getSize()) == 0 &&
                                             Double.compare(max, curTieredBlock.getMax()) == 0) {
                                         return true;
                                     }
                                 }
                               }
                            }
                            return false;
                          }
                      }).orNull();
                      tiers[i] = (overriddenTier != null) ? new DefaultTier(in.getTiers()[i], overriddenTier, currency) : (DefaultTier)in.getTiers()[i] ;
                  }
                  else {
                      tiers[i] = (DefaultTier) in.getTiers()[i];
                  }
              }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrettyName() {
        return prettyName;
    }

    @Override
    public BillingMode getBillingMode() {
        return billingMode;
    }

    @Override
    public UsageType getUsageType() {
        return usageType;
    }

    @Override
    public TierBlockPolicy getTierBlockPolicy() {
        return tierBlockPolicy;
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
        super.initialize(root, uri);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);

        if (prettyName == null) {
            this.prettyName = name;
        }

        for (DefaultLimit limit : limits) {
            limit.initialize(root, uri);
        }
        for (DefaultBlock block : blocks) {
            block.initialize(root, uri);
            block.setPhase(phase);
        }

        for (DefaultTier tier : tiers) {
            tier.initialize(root, uri);
            tier.setPhase(phase);
        }
    }

    public DefaultUsage setBillingPeriod(final BillingPeriod billingPeriod) {
        this.billingPeriod = billingPeriod;
        return this;
    }

    public DefaultUsage setName(final String name) {
        this.name = name;
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

    public DefaultUsage setTierBlockPolicy(final TierBlockPolicy tierBlockPolicy) {
        this.tierBlockPolicy = tierBlockPolicy;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultUsage)) {
            return false;
        }

        final DefaultUsage that = (DefaultUsage) o;

        if (billingMode != that.billingMode) {
            return false;
        }
        if (billingPeriod != that.billingPeriod) {
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
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
            return false;
        }
        if (recurringPrice != null ? !recurringPrice.equals(that.recurringPrice) : that.recurringPrice != null) {
            return false;
        }
        if (!Arrays.equals(tiers, that.tiers)) {
            return false;
        }
        if (usageType != that.usageType) {
            return false;
        }
        if (tierBlockPolicy != that.tierBlockPolicy) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (billingMode != null ? billingMode.hashCode() : 0);
        result = 31 * result + (usageType != null ? usageType.hashCode() : 0);
        result = 31 * result + (tierBlockPolicy != null ? tierBlockPolicy.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (limits != null ? Arrays.hashCode(limits) : 0);
        result = 31 * result + (blocks != null ? Arrays.hashCode(blocks) : 0);
        result = 31 * result + (tiers != null ? Arrays.hashCode(tiers) : 0);
        result = 31 * result + (fixedPrice != null ? fixedPrice.hashCode() : 0);
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        return result;
    }
}
