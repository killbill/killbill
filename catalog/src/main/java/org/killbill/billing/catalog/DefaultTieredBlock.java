/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.api.BlockType;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultTieredBlock extends DefaultBlock implements TieredBlock, Externalizable {

    @XmlElement(required = true)
    private double max;

    @Override
    public Double getMax() {
        return max;
    }

    public DefaultTieredBlock setMax(final double max) {
        this.max = max;
        return this;
    }

    // Required for deserialization
    public DefaultTieredBlock() {
        setType(BlockType.TIERED);
    }

    public DefaultTieredBlock(TieredBlock in, TieredBlockPriceOverride override, Currency currency) {
        super((DefaultUnit) in.getUnit(), in.getSize(), (DefaultInternationalPrice) in.getPrice(), override.getPrice(), currency);
        this.max = in.getMax();
        setType(BlockType.TIERED);
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultTieredBlock that = (DefaultTieredBlock) o;

        return Double.compare(that.max, max) == 0;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        final long temp;
        temp = Double.doubleToLongBits(max);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeDouble(max);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        this.max = in.readDouble();
    }
}
