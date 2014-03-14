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
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.catalog.api.Block;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.util.config.catalog.ValidatingConfig;
import org.killbill.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultBlock extends ValidatingConfig<StandaloneCatalog> implements Block {

    @XmlElement(required = true)
    @XmlIDREF
    private DefaultUnit unit;

    @XmlElement(required = true)
    private Double size;

    @XmlElement(required = true)
    private DefaultInternationalPrice prices;

    // Not defined in catalog
    private DefaultUsage usage;

    @Override
    public Unit getUnit() {
        return unit;
    }

    @Override
    public Double getSize() {
        return size;
    }

    @Override
    public InternationalPrice getPrice() {
        return prices;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog root, final ValidationErrors errors) {
        return errors;
    }

    public DefaultBlock setUnit(final DefaultUnit unit) {
        this.unit = unit;
        return this;
    }

    public DefaultBlock setSize(final Double size) {
        this.size = size;
        return this;
    }

    public DefaultBlock setPrice(final DefaultInternationalPrice prices) {
        this.prices = prices;
        return this;
    }

    public DefaultBlock setUsage(final DefaultUsage usage) {
        this.usage = usage;
        return this;
    }
}
