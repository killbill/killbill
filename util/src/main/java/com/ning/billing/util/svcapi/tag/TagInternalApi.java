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

package com.ning.billing.util.svcapi.tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.ObjectType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

public interface TagInternalApi {

    public List<TagDefinition> getTagDefinitions(InternalTenantContext context);

    /**
     * Return tags for a given object
     *
     * @param objectId   the object id
     * @param objectType the object type
     * @param context    call context
     * @return mapping tag id -> tag
     */
    public List<Tag> getTags(UUID objectId, ObjectType objectType, InternalTenantContext context);

    public void addTag(UUID objectId, ObjectType objectType, UUID tagDefinitionId, InternalCallContext context) throws TagApiException;

    public void removeTag(UUID objectId, ObjectType objectType, UUID tagDefinitionId, InternalCallContext context) throws TagApiException;
}
