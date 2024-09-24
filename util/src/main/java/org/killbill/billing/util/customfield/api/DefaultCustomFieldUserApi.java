/*
 * Copyright 2010-2014 Ning, Inc
 * Copyright 2014-2020 Groupon, Inc
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

package org.killbill.billing.util.customfield.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.commons.utils.Preconditions;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.customfield.StringCustomField;
import org.killbill.billing.util.customfield.dao.CustomFieldDao;
import org.killbill.billing.util.customfield.dao.CustomFieldModelDao;
import org.killbill.billing.util.customfield.dao.DefaultCustomFieldDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultCustomFieldUserApi implements CustomFieldUserApi {
    public static final Function<CustomFieldModelDao, CustomField> CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION = StringCustomField::new;

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
    public Pagination<CustomField> searchCustomFields(final String fieldName, final String fieldValue, final ObjectType objectType, final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<CustomFieldModelDao, CustomFieldApiException>() {
                                                  @Override
                                                  public Pagination<CustomFieldModelDao> build() {
                                                      return customFieldDao.searchCustomFields(fieldName, fieldValue, objectType, offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
                                                  }
                                              },
                                              CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION);
    }


    @Override
    public Pagination<CustomField> searchCustomFields(final String fieldName, final ObjectType objectType, final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<CustomFieldModelDao, CustomFieldApiException>() {
                                                  @Override
                                                  public Pagination<CustomFieldModelDao> build() {
                                                      return customFieldDao.searchCustomFields(fieldName, objectType, offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
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
        if (!customFields.isEmpty()) {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(customFields.get(0).getObjectId(), customFields.get(0).getObjectType(), context);
            final Iterable<CustomFieldModelDao> transformed = customFields.stream()
                    .map(input -> createCustomFieldModelDao(input, context, false))
                    .collect(Collectors.toList());
            ((DefaultCustomFieldDao) customFieldDao).create(transformed, internalCallContext);
        }
    }

    private static CustomFieldModelDao createCustomFieldModelDao(final CustomField input, final CallContext context, final boolean validateCustomFieldId) {
        return createCustomFieldModelDao(input, context.getCreatedDate(), validateCustomFieldId);
    }

    public static CustomFieldModelDao createCustomFieldModelDao(final CustomField input, final DateTime createdDate, final boolean validateCustomFieldId) {
        if (validateCustomFieldId && input.getId() == null) {
            Preconditions.checkNotNull(input.getId(), "createCustomFieldModelDao() input.getId(). This likely happens in updating custom field, where ID is required.");
        }
        // Respect user-specified ID for #addCustomFields()
        // TODO See https://github.com/killbill/killbill/issues/35
        if (input.getId() != null) {
            return new CustomFieldModelDao(input.getId(), createdDate, createdDate, input.getFieldName(), input.getFieldValue(), input.getObjectId(), input.getObjectType());
        } else {
            return new CustomFieldModelDao(createdDate, input.getFieldName(), input.getFieldValue(), input.getObjectId(), input.getObjectType());
        }
    }

    @Override
    public void updateCustomFields(final List<CustomField> customFields, final CallContext context) throws CustomFieldApiException {
        if (!customFields.isEmpty()) {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(customFields.get(0).getObjectId(), customFields.get(0).getObjectType(), context);
            final Iterable<CustomFieldModelDao> customFieldIds = customFields.stream()
                    .map(input -> createCustomFieldModelDao(input, context, true))
                    .collect(Collectors.toList());
            customFieldDao.updateCustomFields(customFieldIds, internalCallContext);

        }
    }

    @Override
    public void removeCustomFields(final List<CustomField> customFields, final CallContext context) throws CustomFieldApiException {
        if (!customFields.isEmpty()) {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(customFields.get(0).getObjectId(), customFields.get(0).getObjectType(), context);
            final Iterable<UUID> customFieldIds = customFields.stream().map(CustomField::getId).collect(Collectors.toUnmodifiableList());
            customFieldDao.deleteCustomFields(customFieldIds, internalCallContext);
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

    @Override
    public List<AuditLogWithHistory> getCustomFieldAuditLogsWithHistoryForId(final UUID customFieldId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return customFieldDao.getCustomFieldAuditLogsWithHistoryForId(customFieldId, auditLevel, internalCallContextFactory.createInternalTenantContext(customFieldId, ObjectType.CUSTOM_FIELD, tenantContext));
    }

    public static List<CustomField> withCustomFieldsTransform(final Collection<CustomFieldModelDao> input) {
        return input.stream().map(CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION).collect(Collectors.toUnmodifiableList());
    }
}
