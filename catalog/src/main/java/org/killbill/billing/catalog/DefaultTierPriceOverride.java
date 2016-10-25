package org.killbill.billing.catalog;

import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;

import java.util.List;

/**
 * Created by sruthipendyala on 10/6/16.
 */
public class DefaultTierPriceOverride implements TierPriceOverride {

    List<TieredBlockPriceOverride> tieredBlockPriceOverrides;

    public DefaultTierPriceOverride(List<TieredBlockPriceOverride> tieredBlockPriceOverrides) {
        this.tieredBlockPriceOverrides = tieredBlockPriceOverrides;
    }

    public List<TieredBlockPriceOverride> getTieredBlockPriceOverrides() {
        return tieredBlockPriceOverrides;
    }

}
