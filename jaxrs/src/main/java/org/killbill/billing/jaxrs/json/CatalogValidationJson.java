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

package org.killbill.billing.jaxrs.json;

import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.catalog.api.CatalogValidation;
import org.killbill.billing.catalog.api.CatalogValidationError;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value = "CatalogValidation")
public class CatalogValidationJson {

    private final List<CatalogValidationErrorJson> catalogValidationErrors;

    @JsonCreator
    public CatalogValidationJson(@JsonProperty("catalogValidationErrors") final List<CatalogValidationErrorJson> catalogValidationErrors) {
        this.catalogValidationErrors = catalogValidationErrors;
    }

    public CatalogValidationJson(final CatalogValidation catalogValidation) {
        final List<CatalogValidationErrorJson> catalogValidationErrorsJson = new LinkedList<>();
        for (final CatalogValidationError catalogValidationError : catalogValidation.getValidationErrors()) {
            catalogValidationErrorsJson.add(new CatalogValidationErrorJson(catalogValidationError.getErrorDescription()));
        }
        this.catalogValidationErrors = catalogValidationErrorsJson;
    }

    public List<CatalogValidationErrorJson> getCatalogValidationErrors() {
        return this.catalogValidationErrors;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CatalogValidationJson{");
        sb.append("catalogValidationErrors='").append(catalogValidationErrors).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final CatalogValidationJson that = (CatalogValidationJson) o;

        return catalogValidationErrors != null ? catalogValidationErrors.equals(that.catalogValidationErrors) : that.catalogValidationErrors == null;
    }

    @Override
    public int hashCode() {
        return catalogValidationErrors != null ? catalogValidationErrors.hashCode() : 0;
    }

    @ApiModel(value = "CatalogValidationError")
    public static class CatalogValidationErrorJson {

        private final String errorDescription;

        @JsonCreator
        public CatalogValidationErrorJson(@JsonProperty("errorDescription") final String errorDescription) {
            this.errorDescription = errorDescription;
        }

        public String getErrorDescription() {
            return this.errorDescription;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("CatalogValidationErrorJson{");
            sb.append("errorDescription='").append(errorDescription).append('\'');
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final CatalogValidationErrorJson that = (CatalogValidationErrorJson) o;

            return errorDescription != null ? errorDescription.equals(that.errorDescription) : that.errorDescription == null;
        }

        @Override
        public int hashCode() {
            return errorDescription != null ? errorDescription.hashCode() : 0;
        }
    }
}
