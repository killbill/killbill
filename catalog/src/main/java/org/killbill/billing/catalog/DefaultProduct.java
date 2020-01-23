/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.Arrays;
import java.util.Collection;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultProduct extends ValidatingConfig<StandaloneCatalog> implements Product, Externalizable {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlAttribute(required = false)
    private String prettyName;

    @XmlElement(required = true)
    private ProductCategory category;

    @XmlElementWrapper(name = "included", required = false)
    @XmlIDREF
    @XmlElement(type = DefaultProduct.class, name = "addonProduct", required = false)
    private CatalogEntityCollection<Product> included;

    @XmlElementWrapper(name = "available", required = false)
    @XmlIDREF
    @XmlElement(type = DefaultProduct.class, name = "addonProduct", required = false)
    private CatalogEntityCollection<Product> available;

    @XmlElementWrapper(name = "limits", required = false)
    @XmlElement(name = "limit", required = false)
    private DefaultLimit[] limits;

    // Not included in XML
    private String catalogName;

    @Override
    public String getCatalogName() {
        return catalogName;
    }

    @Override
    public ProductCategory getCategory() {
        return category;
    }

    @Override
    public Collection<Product> getIncluded() {
        return included.getEntries();
    }

    @Override
    public StaticCatalog getCatalog() {
        return getRoot();
    }

    @Override
    public Collection<Product> getAvailable() {
        return available.getEntries();
    }

    public CatalogEntityCollection<Product> getCatalogEntityCollectionAvailable() {
        return available;
    }

    // Required for deserialization
    public DefaultProduct() {
        this.included = new CatalogEntityCollection<Product>();
        this.available = new CatalogEntityCollection<Product>();
        this.limits = new DefaultLimit[0];
    }

    public DefaultProduct(final String name, final ProductCategory category) {
        this.included = new CatalogEntityCollection<Product>();
        this.available = new CatalogEntityCollection<Product>();
        this.category = category;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrettyName() {
        return prettyName;
    }

    public boolean isIncluded(final DefaultProduct addon) {
        for (final Product p : included.getEntries()) {
            if (addon == p) {
                return true;
            }
        }
        return false;
    }

    public boolean isAvailable(final DefaultProduct addon) {
        for (final Product p : included.getEntries()) {
            if (addon == p) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DefaultLimit[] getLimits() {
        return limits;
    }

    protected Limit findLimit(String unit) {
        for (Limit limit : limits) {
            if (limit.getUnit().getName().equals(unit)) {
                return limit;
            }
        }
        return null;
    }

    @Override
    public boolean compliesWithLimits(String unit, double value) {
        Limit l = findLimit(unit);
        if (l == null) {
            return true;
        }
        return l.compliesWith(value);
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
        for (final DefaultLimit cur : limits) {
            cur.initialize(catalog);
        }
        if (prettyName == null) {
            this.prettyName = name;
        }
        catalogName = catalog.getCatalogName();
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        if (catalogName == null || !catalogName.equals(catalog.getCatalogName())) {
            errors.add(new ValidationError(String.format("Invalid catalogName for product '%s'", name), DefaultProduct.class, ""));

        }
        //TODO: MDW validation: inclusion and exclusion lists can only contain addon products
        //TODO: MDW validation: a given product can only be in, at most, one of inclusion and exclusion lists
        return errors;
    }

    public DefaultProduct setName(final String name) {
        this.name = name;
        return this;
    }

    public DefaultProduct setPrettyName(final String prettyName) {
        this.prettyName = prettyName;
        return this;
    }

    public DefaultProduct setCatagory(final ProductCategory category) {
        this.category = category;
        return this;
    }

    public DefaultProduct setCategory(final ProductCategory category) {
        this.category = category;
        return this;
    }

    public DefaultProduct setIncluded(final Collection<Product> included) {
        this.included = new CatalogEntityCollection<Product>(included);
        return this;
    }

    public DefaultProduct setAvailable(final Collection<Product> available) {
        this.available = new CatalogEntityCollection<Product>(available);
        return this;
    }

    public DefaultProduct setCatalogName(final String catalogName) {
        this.catalogName = catalogName;
        return this;
    }

    @Override
    public String toString() {
        return "DefaultProduct{" +
               "name='" + name + '\'' +
               ", category=" + category +
               ", included=" + included +
               ", available=" + available +
               ", limits=" + Arrays.toString(limits) +
               ", catalogName='" + catalogName + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultProduct)) {
            return false;
        }

        final DefaultProduct product = (DefaultProduct) o;

        if (name != null ? !name.equals(product.name) : product.name != null) {
            return false;
        }
        if (category != product.category) {
            return false;
        }
        if (included != null ? !included.equals(product.included) : product.included != null) {
            return false;
        }
        if (available != null ? !available.equals(product.available) : product.available != null) {
            return false;
        }
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(limits, product.limits)) {
            return false;
        }
        return catalogName != null ? catalogName.equals(product.catalogName) : product.catalogName == null;

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (included != null ? included.hashCode() : 0);
        result = 31 * result + (available != null ? available.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(limits);
        result = 31 * result + (catalogName != null ? catalogName.hashCode() : 0);
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeBoolean(catalogName != null);
        if (catalogName != null) {
            out.writeUTF(catalogName);
        }
        out.writeBoolean(name != null);
        if (name != null) {
            out.writeUTF(name);
        }
        out.writeBoolean(prettyName != null);
        if (prettyName != null) {
            out.writeUTF(prettyName);
        }
        out.writeBoolean(category != null);
        if (category != null) {
            out.writeUTF(category.name());
        }
        out.writeObject(included);
        out.writeObject(available);
        out.writeObject(limits);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.catalogName = in.readBoolean() ? in.readUTF() : null;
        this.name = in.readBoolean() ? in.readUTF() : null;
        this.prettyName = in.readBoolean() ? in.readUTF() : null;
        this.category = in.readBoolean() ? ProductCategory.valueOf(in.readUTF()) : null;
        this.included = (CatalogEntityCollection<Product>) in.readObject();
        this.available = (CatalogEntityCollection<Product>) in.readObject();
        this.limits = (DefaultLimit[]) in.readObject();
    }
}
