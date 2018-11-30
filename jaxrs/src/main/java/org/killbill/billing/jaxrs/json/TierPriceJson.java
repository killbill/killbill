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

package org.killbill.billing.jaxrs.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="TierPrice")
public class TierPriceJson {

    private final List<BlockPriceJson> blockPrices;

    @JsonCreator
    public TierPriceJson(@JsonProperty("blockPrices") final List<BlockPriceJson> blockPrices) {
        this.blockPrices = blockPrices;
    }

    public List<BlockPriceJson> getBlockPrices() {
        return blockPrices;
    }

    @Override
    public String toString() {
        return "TierPriceJson{" +
               "blockPrices='" + blockPrices + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TierPriceJson)) {
            return false;
        }

        final TierPriceJson that = (TierPriceJson) o;

        if (blockPrices != null ? !blockPrices.equals(that.blockPrices) : that.blockPrices != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = blockPrices != null ? blockPrices.hashCode() : 0;
        result = 31 * result + (blockPrices != null ? blockPrices.hashCode() : 0);
        return result;
    }
}
