/*
 * Copyright 2014 The Billing Project, Inc.
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
import org.killbill.billing.catalog.api.TieredBlock;

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

    @Override
    public BlockType getType() {
        return BlockType.TIERED;
    }

    @Override
    public DefaultTieredBlock setType(final BlockType type) {
        super.setType(BlockType.TIERED);
        return this;
    }
}
