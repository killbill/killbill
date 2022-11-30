/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.catalog.api.CatalogValidation;
import org.killbill.billing.catalog.api.CatalogValidationError;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

public class DefaultCatalogValidation implements CatalogValidation {

    private final List<CatalogValidationError> validationErrors = new LinkedList<CatalogValidationError>();

    public DefaultCatalogValidation(final ValidationErrors validationErrors) {

        for (final ValidationError error : validationErrors) {
            this.validationErrors.add(new DefaultCatalogValidationError(error.getDescription()));
        }
    }

    @Override
    public List<CatalogValidationError> getValidationErrors() {
        return validationErrors;
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        for (final CatalogValidationError error : validationErrors) {
            builder.append(error.toString());
        }
        return builder.toString();
    }

}
