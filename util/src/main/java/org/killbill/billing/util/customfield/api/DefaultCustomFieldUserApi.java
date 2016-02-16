/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.util.customfield.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.customfield.StringCustomField;
import org.killbill.billing.util.customfield.dao.CustomFieldDao;
import org.killbill.billing.util.customfield.dao.CustomFieldModelDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultCustomFieldUserApi implements CustomFieldUserApi {

    private static final Function<CustomFieldModelDao, CustomField> CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION = new Function<CustomFieldModelDao, CustomField>() {
        @Override
        public CustomField apply(final CustomFieldModelDao input) {
            return new StringCustomField(input);
        }
    };

    private final InternalCallContextFactory internalCallContextFactory;
    private final CustomFieldDao customFieldDao;

    @Inject
    public DefaultCustomFieldUserApi(final InternalCallContextFactory internalCallContextFactory, final CustomFieldDao customFieldDao) {
        this.internalCallContextFactory = internalCallContextFactory;
        this.customFieldDao = customFieldDao;
    }

    @Override
    public Pagination<CustomField> searchCustomFields(final String searchKey, final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<CustomFieldModelDao, CustomFieldApiException>() {
                                                  @Override
                                                  public Pagination<CustomFieldModelDao> build() {
                                                      return customFieldDao.searchCustomFields(searchKey, offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
                                                  }
                                              },
                                              CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION);
    }

    @Override
    public Pagination<CustomField> getCustomFields(final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<CustomFieldModelDao, CustomFieldApiException>() {
                                                  @Override
                                                  public Pagination<CustomFieldModelDao> build() {
                                                      return customFieldDao.get(offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
                                                  }
                                              },
                                              CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION);
    }

    @Override
    public void addCustomFields(final List<CustomField> customFields, final CallContext context) throws CustomFieldApiException {
        // TODO make it transactional

        final Map<UUID, ObjectType> mapping = new HashMap<UUID, ObjectType>();
        for (final CustomField cur : customFields) {
            mapping.put(cur.getObjectId(), cur.getObjectType());
        }

        final List<CustomFieldModelDao> all = new LinkedList<CustomFieldModelDao>();
        for (UUID cur : mapping.keySet()) {
            final ObjectType type = mapping.get(cur);
            all.addAll(customFieldDao.getCustomFieldsForObject(cur, type, internalCallContextFactory.createInternalCallContext(cur, type, context)));
        }
        final List<CustomField> toBeInserted = new LinkedList<CustomField>();
        for (final CustomField cur : customFields) {
            final CustomFieldModelDao match = Iterables.tryFind(all, new com.google.common.base.Predicate<CustomFieldModelDao>() {
                @Override
                public boolean apply(final CustomFieldModelDao input) {
                    return input.getObjectId().equals(cur.getObjectId()) &&
                           input.getObjectType() == cur.getObjectType() &&
                           input.getFieldName().equals(cur.getFieldName());

                }
            }).orNull();
            if (match != null) {
                throw new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_ALREADY_EXISTS, match.getId());
            }
            toBeInserted.add(cur);
        }

        for (CustomField cur : toBeInserted) {
            customFieldDao.create(new CustomFieldModelDao(context.getCreatedDate(), cur.getFieldName(), cur.getFieldValue(), cur.getObjectId(), cur.getObjectType()), internalCallContextFactory.createInternalCallContext(cur.getObjectId(), cur.getObjectType(), context));
        }
    }

    @Override
    public void removeCustomFields(final List<CustomField> customFields, final CallContext context) throws CustomFieldApiException {
        // TODO make it transactional
        for (final CustomField cur : customFields) {
            customFieldDao.deleteCustomField(cur.getId(), internalCallContextFactory.createInternalCallContext(cur.getObjectId(), cur.getObjectType(), context));
        }
    }

    @Override
    public List<CustomField> getCustomFieldsForObject(final UUID objectId, final ObjectType objectType, final TenantContext context) {
        return withCustomFieldsTransform(customFieldDao.getCustomFieldsForObject(objectId, objectType, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context)));
    }

    @Override
    public List<CustomField> getCustomFieldsForAccountType(final UUID accountId, final ObjectType objectType, final TenantContext context) {
        return withCustomFieldsTransform(customFieldDao.getCustomFieldsForAccountType(objectType, internalCallContextFactory.createInternalTenantContext(accountId, context)));
    }

    @Override
    public List<CustomField> getCustomFieldsForAccount(final UUID accountId, final TenantContext context) {
        return withCustomFieldsTransform(customFieldDao.getCustomFieldsForAccount(internalCallContextFactory.createInternalTenantContext(accountId, context)));
    }

    private List<CustomField> withCustomFieldsTransform(final Collection<CustomFieldModelDao> input) {
        return ImmutableList.<CustomField>copyOf(Collections2.transform(input, CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION));
    }
}
