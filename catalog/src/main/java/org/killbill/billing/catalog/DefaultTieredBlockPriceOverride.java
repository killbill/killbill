package org.killbill.billing.catalog;

import org.killbill.billing.catalog.api.BlockType;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.Unit;

import java.math.BigDecimal;

/**
 * Created by sruthipendyala on 10/6/16.
 */
public class DefaultTieredBlockPriceOverride extends DefaultBlockPriceOverride implements TieredBlockPriceOverride{

    private Double max;

    @Override
    public Double getMax() {
        return max;
    }

    public DefaultTieredBlockPriceOverride(String unitName, Double size, BigDecimal price, Double max) {
        super(unitName, size, price);
        this.max = max;
    }


}
