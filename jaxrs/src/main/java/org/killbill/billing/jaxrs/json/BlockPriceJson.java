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

import java.math.BigDecimal;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="BlockPrice")
public class BlockPriceJson {

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
    public BlockPriceJson(@Nullable @JsonProperty("unitName") final String unitName,
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
        return "BlockPriceJson{" +
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
        if (!(o instanceof BlockPriceJson)) {
            return false;
        }

        final BlockPriceJson that = (BlockPriceJson) o;


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
