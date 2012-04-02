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

package com.ning.billing.util.tag.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.List;
import java.util.UUID;

public interface TagDao {
    void saveTagsFromTransaction(Transmogrifier dao, UUID objectId, String objectType, List<Tag> tags, CallContext context);

    void saveTags(UUID objectId, String objectType, List<Tag> tags, CallContext context);

    List<Tag> loadTags(UUID objectId, String objectType);

    List<Tag> loadTagsFromTransaction(Transmogrifier dao, UUID objectId, String objectType);

    void addTag(String tagName, UUID objectId, String objectType, CallContext context);

    void removeTag(String tagName, UUID objectId, String objectType, CallContext context);
}
