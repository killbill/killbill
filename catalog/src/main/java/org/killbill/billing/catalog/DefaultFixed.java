/*
 * Copyright 2014 The Billing Project, LLC
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

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.api.Fixed;
import org.killbill.billing.catalog.api.FixedType;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.util.config.catalog.ValidatingConfig;
import org.killbill.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultFixed extends ValidatingConfig<StandaloneCatalog> implements Fixed {


    @XmlAttribute(required = false)
    private FixedType type = FixedType.ONE_TIME;

    @XmlElement(required = false)
    private DefaultInternationalPrice fixedPrice;

    // Not exposed in xml.
    private PlanPhase phase;

    @Override
    public FixedType getType() {
        return type;
    }

    @Override
    public InternationalPrice getPrice() {
        return fixedPrice;
    }


    @Override
    public void initialize(final StandaloneCatalog root, final URI uri) {
        if (fixedPrice != null) {
            fixedPrice.initialize(root, uri);
        }
    }
        @Override
    public ValidationErrors validate(final StandaloneCatalog root, final ValidationErrors errors) {
        return errors;
    }

    protected DefaultFixed setType(final FixedType type) {
        this.type = type;
        return this;
    }

    protected DefaultFixed setFixedPrice(final DefaultInternationalPrice fixedPrice) {
        this.fixedPrice = fixedPrice;
        return this;
    }

    protected DefaultFixed setPhase(final PlanPhase phase) {
        this.phase = phase;
        return this;
    }
}
