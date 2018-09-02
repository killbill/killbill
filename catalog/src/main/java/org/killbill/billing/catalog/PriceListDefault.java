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

package org.killbill.billing.catalog;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class PriceListDefault extends DefaultPriceList {

    public PriceListDefault() {
    }

    public PriceListDefault(final DefaultPlan[] defaultPlans) {
        super(defaultPlans, PriceListSet.DEFAULT_PRICELIST_NAME);
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        super.validate(catalog, errors);
        if (!getName().equals(PriceListSet.DEFAULT_PRICELIST_NAME)) {
            errors.add(new ValidationError("The name of the default pricelist must be 'DEFAULT'",
                                           DefaultPriceList.class, getName()));

        }
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }

    @Override
    public String getName() {
        return PriceListSet.DEFAULT_PRICELIST_NAME;
    }

}
