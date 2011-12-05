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

package com.ning.billing.account.dao;

import com.google.inject.Inject;
import com.ning.billing.account.api.CustomField;
import org.skife.jdbi.v2.IDBI;

import java.util.List;

public class FieldStoreDaoWrapper implements FieldStoreDao {
    private final FieldStoreDao dao;

    @Inject
    public FieldStoreDaoWrapper(IDBI dbi) {
        dao = dbi.onDemand(FieldStoreDao.class);
    }

    @Override
    public void save(String objectId, String objectType, List<CustomField> entities) {
        dao.save(objectId, objectType, entities);
    }

    @Override
    public List<CustomField> load(String objectId, String objectType) {
        return dao.load(objectId, objectType);
    }

    @Override
    public void test() {
        dao.test();
    }
}
