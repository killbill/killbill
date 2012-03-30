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

package com.ning.billing.util.tag.api;

import com.google.inject.Inject;
import com.ning.billing.util.api.TagDefinitionService;
import com.ning.billing.util.api.TagUserApi;

public class DefaultTagDefinitionService implements TagDefinitionService {
    private static final String TAG_DEFINITION_SERVICE_NAME = "tag-service";
    private final TagUserApi api;

    @Inject
    public DefaultTagDefinitionService(final TagUserApi api) {
        this.api = api;
    }

    @Override
    public TagUserApi getTagDefinitionUserApi() {
        return api;
    }

    @Override
    public String getName() {
        return TAG_DEFINITION_SERVICE_NAME;
    }
}
