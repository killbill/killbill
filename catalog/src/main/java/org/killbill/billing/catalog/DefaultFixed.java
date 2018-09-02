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
import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.api.Fixed;
import org.killbill.billing.catalog.api.FixedType;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultFixed extends ValidatingConfig<StandaloneCatalog> implements Fixed, Externalizable {

    @XmlAttribute(required = false)
    private FixedType type;

    @XmlElement(required = false)
    private DefaultInternationalPrice fixedPrice;

    @Override
    public FixedType getType() {
        return type;
    }

    @Override
    public InternationalPrice getPrice() {
        return fixedPrice;
    }

    // Required for deserialization
    public DefaultFixed() {
    }

    public DefaultFixed(final DefaultFixed in, final PlanPhasePriceOverride override) {
        this.type = in.getType();
        this.fixedPrice = in.getPrice() != null ? new DefaultInternationalPrice((DefaultInternationalPrice) in.getPrice(), override, true) : null;
    }

    @Override
    public void initialize(final StandaloneCatalog root) {
        super.initialize(root);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
        if (fixedPrice != null) {
            fixedPrice.initialize(root);
        }
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog root, final ValidationErrors errors) {
        // Safety check
        if (type == null) {
            throw new IllegalStateException("fixedPrice should have been automatically been initialized with ONE_TIME ");
        }
        return errors;
    }

    public DefaultFixed setType(final FixedType type) {
        this.type = type;
        return this;
    }

    public DefaultFixed setFixedPrice(final DefaultInternationalPrice fixedPrice) {
        this.fixedPrice = fixedPrice;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultFixed)) {
            return false;
        }

        final DefaultFixed that = (DefaultFixed) o;

        if (fixedPrice != null ? !fixedPrice.equals(that.fixedPrice) : that.fixedPrice != null) {
            return false;
        }
        if (type != that.type) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (fixedPrice != null ? fixedPrice.hashCode() : 0);
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeBoolean(type != null);
        if (type != null) {
            out.writeUTF(type.name());
        }
        out.writeObject(fixedPrice);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.type = in.readBoolean() ? FixedType.valueOf(in.readUTF()) : null;
        this.fixedPrice = (DefaultInternationalPrice) in.readObject();
    }
}
