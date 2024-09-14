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

package org.killbill.billing.util.customfield;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.customfield.CustomFieldInternalApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.customfield.dao.CustomFieldDao;
import org.killbill.billing.util.customfield.dao.CustomFieldModelDao;
import org.killbill.billing.util.customfield.dao.DefaultCustomFieldDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.commons.utils.collect.Iterators;
import org.skife.jdbi.v2.Handle;

import static org.killbill.billing.util.customfield.api.DefaultCustomFieldUserApi.CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION;
import static org.killbill.billing.util.customfield.api.DefaultCustomFieldUserApi.createCustomFieldModelDao;
import static org.killbill.billing.util.customfield.api.DefaultCustomFieldUserApi.withCustomFieldsTransform;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultCustomFieldInternalApi implements CustomFieldInternalApi {

    private final CustomFieldDao customFieldDao;

    @Inject
    public DefaultCustomFieldInternalApi(final CustomFieldDao customFieldDao) {
        this.customFieldDao = customFieldDao;
    }

    @Override
    public CustomField searchUniqueCustomField(final String key, final String value, final ObjectType objectType, final InternalTenantContext context) {
        final String searchKey = String.format("_q=1&object_type=%s&field_name=%s&field_value=%s", objectType, key, value);
        final Pagination<CustomField> page = getEntityPaginationNoException(1L,
                                                                                    new SourcePaginationBuilder<CustomFieldModelDao, CustomFieldApiException>() {
                                                                                        @Override
                                                                                        public Pagination<CustomFieldModelDao> build() {
                                                                                            return customFieldDao.searchCustomFields(searchKey, 0L, 1L, context);
                                                                                        }
                                                                                    },
                                                                                    CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION);
        return Iterators.<CustomField>getLast(page.iterator(), null);
    }

    @Override
    public List<CustomField> getCustomFieldsForObject(final UUID objectId, final ObjectType objectType, final InternalTenantContext context) {
        return withCustomFieldsTransform(customFieldDao.getCustomFieldsForObject(objectId, objectType, context));
    }

    @Override
    public List<CustomField> getCustomFieldsForAccountType(final ObjectType objectType, final InternalTenantContext context) {
        return withCustomFieldsTransform(customFieldDao.getCustomFieldsForAccountType(objectType, context));
    }

    @Override
    public void addCustomFieldsFromTransaction(final Handle handle, final List<CustomField> customFields, final InternalCallContext context) throws CustomFieldApiException {
        if (!customFields.isEmpty()) {
            final List<CustomFieldModelDao> transformed = customFields.stream()
                                                                      .map(input -> createCustomFieldModelDao(input, context.getCreatedDate(), false))
                                                                      .collect(Collectors.toList());
            ((DefaultCustomFieldDao) customFieldDao).create(handle, transformed, context);
        }
    }
}
