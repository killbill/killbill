/*
 * Copyright 2010-2011 Ning, Inc.
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

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;

import org.killbill.billing.catalog.api.Unit;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultUnit extends ValidatingConfig<StandaloneCatalog> implements Unit {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlAttribute(required = false)
    private String prettyName;

    /* (non-Javadoc)
     * @see org.killbill.billing.catalog.Unit#getName()
     */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrettyName() {
        return prettyName;
    }

    @Override
    public ValidationErrors validate(StandaloneCatalog root, ValidationErrors errors) {
        return errors;
    }


    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
        if (prettyName == null) {
            this.prettyName = name;
        }
    }

    public DefaultUnit setName(final String name) {
        this.name = name;
        return this;
    }

    public DefaultUnit setPrettyName(final String prettyName) {
        this.prettyName = prettyName;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultUnit)) {
            return false;
        }

        final DefaultUnit that = (DefaultUnit) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
