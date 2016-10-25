package org.killbill.billing.catalog;

import org.killbill.billing.catalog.api.BlockPriceOverride;
import org.killbill.billing.catalog.api.BlockType;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Unit;

import java.math.BigDecimal;

/**
 * Created by sruthipendyala on 10/6/16.
 */
public class DefaultBlockPriceOverride implements BlockPriceOverride {


    private String unitName;

    private Double size;

    private BigDecimal price;



    @Override
    public String getUnitName() {
        return unitName;
    }

    @Override
    public Double getSize() {
        return size;
    }

    @Override
    public BigDecimal getPrice() {
        return price;
    }

    public DefaultBlockPriceOverride( String unitName, Double size, BigDecimal price) {
        this.unitName = unitName;
        this.size = size;
        this.price = price;
    }

}
