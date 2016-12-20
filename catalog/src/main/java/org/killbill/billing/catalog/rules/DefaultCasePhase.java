/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.catalog.rules;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.CatalogSafetyInitializer;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.xmlloader.ValidationErrors;

public abstract class DefaultCasePhase<T> extends DefaultCaseStandardNaming<T> {

    @XmlElement(required = false)
    protected PhaseType phaseType;

    public T getResult(final PlanPhaseSpecifier specifier, final StaticCatalog c) throws CatalogApiException {
        if ((phaseType == null || specifier.getPhaseType() == phaseType)
                && satisfiesCase(new PlanSpecifier(specifier), c)) {
            return getResult();
        }
        return null;
    }

    public static <K> K getResult(final DefaultCasePhase<K>[] cases, final PlanPhaseSpecifier planSpec, final StaticCatalog catalog) throws CatalogApiException {
        if (cases != null) {
            for (final DefaultCasePhase<K> cp : cases) {
                final K result = cp.getResult(planSpec, catalog);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;

    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }


    public DefaultCasePhase<T> setPhaseType(final PhaseType phaseType) {
        this.phaseType = phaseType;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCasePhase)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultCasePhase that = (DefaultCasePhase) o;

        if (phaseType != that.phaseType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (phaseType != null ? phaseType.hashCode() : 0);
        return result;
    }
}
