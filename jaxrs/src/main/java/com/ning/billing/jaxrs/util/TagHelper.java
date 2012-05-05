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
package com.ning.billing.jaxrs.util;

import java.util.LinkedList;
import java.util.List;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.tag.TagDefinition;

public class TagHelper {

    private final TagUserApi tagUserApi;
    
    @Inject
    public TagHelper(final TagUserApi tagUserApi) {
        this.tagUserApi = tagUserApi;
    }
    
    public List<TagDefinition> getTagDifinitionFromTagList(final String tagList) throws TagDefinitionApiException {
        List<TagDefinition> result = new LinkedList<TagDefinition>();
        String [] tagParts = tagList.split(",\\s*");
        for (String cur : tagParts) {
            TagDefinition curDef = tagUserApi.getTagDefinition(cur);
            // Yack should throw excption
            if (curDef == null) {
                throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, cur);
            }
            result.add(curDef);
        }
        return result;
    }
}
