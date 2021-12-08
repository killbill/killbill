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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.util.catalog.CatalogDateHelper;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlRootElement(name = "catalogs")
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultVersionedCatalog extends ValidatingConfig<DefaultVersionedCatalog> implements VersionedCatalog, Externalizable {

    private static final long serialVersionUID = 3181874902672322725L;
    @XmlElementWrapper(name = "versions", required = true)
    @XmlElement(name = "version", type = StandaloneCatalog.class, required = true)
    private final List<StaticCatalog> versions;
    @XmlElement(required = true)
    private String catalogName;

    // Required for JAXB deserialization
    public DefaultVersionedCatalog() {
        this.versions = new ArrayList<StaticCatalog>();
    }

    @Override
    public String getCatalogName() {
        return !versions.isEmpty() ? versions.get(0).getCatalogName() : null;
    }

    @Override
    public List<StaticCatalog> getVersions() {
        return versions;
    }

    @Override
    public StaticCatalog getCurrentVersion() {
        // Looks like we sometimes cache empty catalog ?
        return !versions.isEmpty() ? versions.get(versions.size() - 1) : null;
    }

    @Override
    public StaticCatalog getVersion(final Date date) {
        return versionForDate(CatalogDateHelper.toUTCDateTime(date));
    }

    private StaticCatalog versionForDate(final DateTime date) {
        return versions.get(indexOfVersionForDate(date.toDate()));
    }

    private int indexOfVersionForDate(final Date date) {

        for (int i = versions.size() - 1; i >= 0; i--) {
            final StaticCatalog c = versions.get(i);
            if (c.getEffectiveDate().getTime() <= date.getTime()) {
                return i;
            }
        }
        // If the only version we have are after the input date, we return the first version
        // This is not strictly correct from an api point of view, but there is no real good use case
        // where the system would ask for the catalog for a date prior any catalog was uploaded and
        // yet time manipulation could end of inn that state -- see https://github.com/killbill/killbill/issues/760
        if (!versions.isEmpty()) {
            return 0;
        }
        throw new IllegalStateException(String.format("No existing versions in the VersionedCatalog catalog for input date %s", date));
    }

    public void add(final StandaloneCatalog e) {
        if (catalogName == null && e.getCatalogName() != null) {
            catalogName = e.getCatalogName();
        }
        versions.add(e);
        Collections.sort(versions, new Comparator<StaticCatalog>() {
            @Override
            public int compare(final StaticCatalog c1, final StaticCatalog c2) {
                return c1.getEffectiveDate().compareTo(c2.getEffectiveDate());
            }
        });
    }

    @Override
    public void initialize(final DefaultVersionedCatalog catalog) {
        //
        // Initialization is performed first on each StandaloneCatalog (XMLLoader#initializeAndValidate)
        // and then later on the VersionedCatalog, so we only initialize and validate VersionedCatalog
        // *without** recursively through each StandaloneCatalog
        //
        super.initialize(catalog);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }

    @Override
    public ValidationErrors validate(final DefaultVersionedCatalog catalog, final ValidationErrors errors) {
        final Set<Date> effectiveDates = new TreeSet<Date>();

        for (final StaticCatalog c : versions) {
            if (effectiveDates.contains(c.getEffectiveDate())) {
                errors.add(new ValidationError(String.format("Catalog effective date '%s' already exists for a previous version", c.getEffectiveDate()),
                                               DefaultVersionedCatalog.class, ""));
            } else {
                effectiveDates.add(c.getEffectiveDate());
            }
            if (!c.getCatalogName().equals(catalogName)) {
                errors.add(new ValidationError(String.format("Catalog name '%s' is not consistent across versions ", c.getCatalogName()),
                                               DefaultVersionedCatalog.class, ""));
            }
            errors.addAll(((StandaloneCatalog) c).validate((StandaloneCatalog) c, errors));
        }

        validateUniformPlanShapeAcrossVersions(errors);

        return errors;
    }

    private void validateUniformPlanShapeAcrossVersions(final ValidationErrors errors) {
        for (int i = 0; i < versions.size(); i++) {
            final StaticCatalog c = versions.get(i);
            for (final Plan plan : ((StandaloneCatalog) c).getPlans()) {

                for (int j = i + 1; j < versions.size(); j++) {
                    final StaticCatalog next = versions.get(j);
                    final Plan targetPlan = ((StandaloneCatalog) next).getPlansMap().findByName(plan.getName());
                    if (targetPlan != null) {
                        validatePlanShape(plan, targetPlan, errors);
                    }
                    // We don't break if null , targetPlan could be re-defined on a subsequent version
                    // TODO enforce that we can't skip versions?
                }
            }
        }
    }

    private void validatePlanShape(final Plan plan, final Plan targetPlan, final ValidationErrors errors) {
        if (plan.getAllPhases().length != targetPlan.getAllPhases().length) {
            errors.add(new ValidationError(String.format("Number of phases for plan '%s' differs between version '%s' and '%s'",
                                                         plan.getName(), plan.getCatalog().getEffectiveDate(), targetPlan.getCatalog().getEffectiveDate()),
                                           DefaultVersionedCatalog.class, ""));
            // In this case we don't bother checking each phase -- the code below assumes the # are equal
            return;
        }

        for (int i = 0; i < plan.getAllPhases().length; i++) {
            final PlanPhase cur = plan.getAllPhases()[i];
            final PlanPhase target = targetPlan.getAllPhases()[i];
            if (!cur.getName().equals(target.getName())) {
               errors.add(new ValidationError(String.format("Phase '%s'for plan '%s' in version '%s' does not exist in version '%s'",
                                                             cur.getName(), plan.getName(), plan.getCatalog().getEffectiveDate(), targetPlan.getCatalog().getEffectiveDate()),
                                               DefaultVersionedCatalog.class, ""));
            }
        }
    }
    

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.catalogName = in.readBoolean() ? in.readUTF() : null;
        this.versions.addAll((Collection<? extends StandaloneCatalog>) in.readObject());
    }

    @Override
    public void writeExternal(final ObjectOutput oo) throws IOException {
        oo.writeBoolean(catalogName != null);
        if (catalogName != null) {
            // Can be null for placeholder XML
            oo.writeUTF(catalogName);
        }
        oo.writeObject(versions);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultVersionedCatalog that = (DefaultVersionedCatalog) o;

        if (versions != null ? !versions.equals(that.versions) : that.versions != null) {
            return false;
        }
        return catalogName != null ? catalogName.equals(that.catalogName) : that.catalogName == null;
    }

    @Override
    public int hashCode() {
        int result = versions != null ? versions.hashCode() : 0;
        result = 31 * result + (catalogName != null ? catalogName.hashCode() : 0);
        return result;
    }

}
