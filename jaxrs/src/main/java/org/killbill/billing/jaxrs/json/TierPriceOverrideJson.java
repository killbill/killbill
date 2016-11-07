package org.killbill.billing.jaxrs.json;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TierPriceOverrideJson {

    private final List<BlockPriceOverrideJson> blockPriceOverrides;

    public List<BlockPriceOverrideJson> getBlockPriceOverrides() {
        return blockPriceOverrides;
    }

    @JsonCreator
    public TierPriceOverrideJson(@JsonProperty("blockPriceOverrides") final List<BlockPriceOverrideJson> blockPriceOverrides) {
        this.blockPriceOverrides = blockPriceOverrides;
    }
}
