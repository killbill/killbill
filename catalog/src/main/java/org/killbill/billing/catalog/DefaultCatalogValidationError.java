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

import org.killbill.billing.catalog.api.CatalogValidationError;

public class DefaultCatalogValidationError implements CatalogValidationError {

    private final String errorDescription;

    public DefaultCatalogValidationError(final String errorDescription) {
        this.errorDescription = errorDescription;
    }

    @Override
    public String getErrorDescription() {
        return errorDescription;
    }

    @Override
    public String toString() {
    	return errorDescription;
    }

}
