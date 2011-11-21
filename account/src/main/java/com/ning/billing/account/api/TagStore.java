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

package com.ning.billing.account.api;

import com.ning.billing.account.dao.IEntityCollectionDao;
import com.ning.billing.account.glue.InjectorMagic;

import java.util.UUID;

public class TagStore extends EntityCollectionBase<Tag> {
    public TagStore(UUID objectId, String objectType) {
        super(objectId, objectType);
    }

    @Override
    protected String getEntityKey(Tag entity) {
        return entity.getDescription();
    }

    @Override
    protected IEntityCollectionDao<Tag> getCollectionDao() {
        return InjectorMagic.getTagStoreDao();
    }
}
