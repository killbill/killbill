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

package com.ning.billing.util.customfield.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ning.billing.ObjectType;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.entity.Pagination;
import com.ning.billing.util.entity.dao.MockEntityDaoBase;

public class MockCustomFieldDao extends MockEntityDaoBase<CustomFieldModelDao, CustomField, CustomFieldApiException> implements CustomFieldDao {

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForObject(final UUID objectId, final ObjectType objectType, final InternalTenantContext context) {
        final List<CustomFieldModelDao> result = new ArrayList<CustomFieldModelDao>();
        final Iterable<CustomFieldModelDao> all = getAll(context);
        for (final CustomFieldModelDao cur : all) {
            if (cur.getObjectId().equals(objectId) && cur.getObjectType() == objectType) {
                result.add(cur);
            }
        }
        return result;
    }

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForAccountType(final ObjectType objectType, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForAccount(final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pagination<CustomFieldModelDao> searchCustomFields(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }
}
