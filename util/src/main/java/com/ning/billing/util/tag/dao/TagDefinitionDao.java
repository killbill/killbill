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

import java.util.List;

import com.ning.billing.util.CallContext;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.tag.TagDefinition;

public interface TagDefinitionDao {
    public List<TagDefinition> getTagDefinitions();

    public TagDefinition getByName(String definitionName);

    public TagDefinition create(String definitionName, String description, CallContext context) throws TagDefinitionApiException;

    public void deleteAllTagsForDefinition(String definitionName, CallContext context) throws TagDefinitionApiException;

    public void deleteTagDefinition(String definitionName, CallContext context) throws TagDefinitionApiException;
}
