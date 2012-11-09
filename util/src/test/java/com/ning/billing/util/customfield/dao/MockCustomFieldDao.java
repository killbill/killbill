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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.ObjectType;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.entity.dao.MockEntityDaoBase;

public class MockCustomFieldDao extends MockEntityDaoBase<CustomField, CustomFieldApiException> implements CustomFieldDao {

    @Override
    public List<CustomField> getCustomFields(final UUID objectId, final ObjectType objectType, final InternalTenantContext context) {
        List<CustomField> result =  new ArrayList<CustomField>();
        List<CustomField> all = get(context);
        for (CustomField cur : all) {
            if (cur.getObjectId().equals(objectId) && cur.getObjectType() == objectType) {
                result.add(cur);
            }
        }
        return result;
    }
}
