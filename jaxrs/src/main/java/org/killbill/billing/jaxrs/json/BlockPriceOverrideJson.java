package org.killbill.billing.jaxrs.json;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.killbill.billing.catalog.api.BlockType;

import javax.annotation.Nullable;
import java.math.BigDecimal;

public class BlockPriceOverrideJson {


    private String unitName;

    private Double size;

    private BigDecimal price;

    private Double max;

    public BigDecimal getPrice() {
        return price;
    }

    public Double getSize() {
        return size;
    }

    public String getUnitName() {
        return unitName;
    }

    public Double getMax() {
        return max;
    }

    @JsonCreator
    public BlockPriceOverrideJson(@Nullable @JsonProperty("unitName") final String unitName,
                                  @Nullable @JsonProperty("size") final Double size,
                                  @Nullable @JsonProperty("price") final BigDecimal price,
                                  @Nullable @JsonProperty("max") final Double max) {
        this.unitName = unitName;
        this.size = size;
        this.price = price;
        this.max = max;
    }

    @Override
    public String toString() {
        return "BlockPriceOverrideJson{" +
              "unitName='" + unitName + '\'' +
                ",size=" + size +
                ",price=" + price +
                ",max=" + max +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockPriceOverrideJson)) {
            return false;
        }

        final BlockPriceOverrideJson that = (BlockPriceOverrideJson) o;


        if (unitName != null ? !unitName.equals(that.unitName) : that.unitName != null) {
            return false;
        }
        if (size != null ? size.compareTo(that.size) != 0 : that.size != null) {
            return false;
        }

        if (price != null ? price.compareTo(that.price) != 0 : that.price != null) {
            return false;
        }

        if (max != null ? max.compareTo(that.max) != 0 : that.max != null) {
            return false;
        }
        return true;
    }


}
