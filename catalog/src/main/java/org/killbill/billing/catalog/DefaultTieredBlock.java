/*
 * Copyright 2014 The Billing Project, LLC
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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.api.BlockType;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultTieredBlock extends DefaultBlock implements TieredBlock {

    @XmlElement(required = true)
    private Double max;

    @Override
    public Double getMax() {
        return max;
    }

    public DefaultTieredBlock setMax(final Double max) {
        this.max = max;
        return this;
    }

    public DefaultTieredBlock() {
    }

    public DefaultTieredBlock(TieredBlock in, TieredBlockPriceOverride override, Currency currency) {
        super((DefaultUnit)in.getUnit(), in.getSize(),(DefaultInternationalPrice)in.getPrice(), override.getPrice(),currency);
        this.max = in.getMax();
    }

    @Override
    public BlockType getType() {
        return BlockType.TIERED;
    }

    @Override
    public DefaultTieredBlock setType(final BlockType type) {
        super.setType(BlockType.TIERED);
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultTieredBlock)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultTieredBlock that = (DefaultTieredBlock) o;

        if (max != null ? !max.equals(that.max) : that.max != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (max != null ? max.hashCode() : 0);
        return result;
    }
}
