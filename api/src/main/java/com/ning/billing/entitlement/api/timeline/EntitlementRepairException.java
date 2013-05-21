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

package com.ning.billing.entitlement.api.timeline;

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;

public class EntitlementRepairException extends BillingExceptionBase {

    private static final long serialVersionUID = 19067233L;

    public EntitlementRepairException(final EntitlementUserApiException e) {
        super(e, e.getCode(), e.getMessage());
    }

    public EntitlementRepairException(final CatalogApiException e) {
        super(e, e.getCode(), e.getMessage());
    }

    public EntitlementRepairException(final Throwable e, final ErrorCode code, final Object... args) {
        super(e, code, args);
    }

    public EntitlementRepairException(final ErrorCode code, final Object... args) {
        super(code, args);
    }
}
