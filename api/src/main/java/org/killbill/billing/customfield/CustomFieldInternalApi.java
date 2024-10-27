/*
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.customfield;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.customfield.CustomField;

import org.skife.jdbi.v2.Handle;

public interface CustomFieldInternalApi {

    CustomField searchUniqueCustomField(final String key, final String value, final ObjectType objectType, final InternalTenantContext context);

    List<CustomField> getCustomFieldsForObject(final UUID objectId, final ObjectType objectType, final InternalTenantContext context);

    List<CustomField> getCustomFieldsForAccountType(final ObjectType objectType, final InternalTenantContext context);

    void addCustomFieldsFromTransaction(final Handle handle, final List<CustomField> customFields, final InternalCallContext context) throws CustomFieldApiException;
}
