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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
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

import com.google.common.base.Function;
import com.google.inject.Inject;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultCustomFieldUserApi implements CustomFieldUserApi {

    /**
     * FIXME-1615 : Cannot replaced by java.util.function.Function because
     * {@link org.killbill.billing.util.entity.dao.DefaultPaginationHelper#getEntityPaginationNoException(Long, SourcePaginationBuilder, Function)}.
     * used by several modules: (killbill-account, killbill-entitlement, killbill-invoice, killbill-subscription)
     */
    private static final Function<CustomFieldModelDao, CustomField> CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION = StringCustomField::new;

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
                    .map(input -> {
                        if (input.getId() != null) {
                            return new CustomFieldModelDao(input.getId(), context.getCreatedDate(), context.getCreatedDate(), input.getFieldName(), input.getFieldValue(), input.getObjectId(), input.getObjectType());
                        } else {
                            return new CustomFieldModelDao(context.getCreatedDate(), input.getFieldName(), input.getFieldValue(), input.getObjectId(), input.getObjectType());
                        }
                    })
                    .collect(Collectors.toList());
            ((DefaultCustomFieldDao) customFieldDao).create(transformed, internalCallContext);
        }
    }

    @Override
    public void updateCustomFields(final List<CustomField> customFields, final CallContext context) throws CustomFieldApiException {
        if (!customFields.isEmpty()) {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(customFields.get(0).getObjectId(), customFields.get(0).getObjectType(), context);
            final Iterable<CustomFieldModelDao> customFieldIds = customFields.stream()
                    .map(input -> new CustomFieldModelDao(input.getId(), internalCallContext.getCreatedDate(), internalCallContext.getUpdatedDate(), input.getFieldName(), input.getFieldValue(), input.getObjectId(), input.getObjectType()))
                    .collect(Collectors.toList());
            customFieldDao.updateCustomFields(customFieldIds, internalCallContext);

        }
    }

    @Override
    public void removeCustomFields(final List<CustomField> customFields, final CallContext context) throws CustomFieldApiException {
        if (!customFields.isEmpty()) {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(customFields.get(0).getObjectId(), customFields.get(0).getObjectType(), context);
            final Iterable<UUID> curstomFieldIds = customFields.stream().map(CustomField::getId).collect(Collectors.toList());
            customFieldDao.deleteCustomFields(curstomFieldIds, internalCallContext);
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

    private List<CustomField> withCustomFieldsTransform(final Collection<CustomFieldModelDao> input) {
        return List.copyOf(input.stream().map(CUSTOM_FIELD_MODEL_DAO_CUSTOM_FIELD_FUNCTION).collect(Collectors.toList()));
    }
}
