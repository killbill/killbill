/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice;

import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.tag.Tag;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.util.tag.dao.SystemTags.PARK_TAG_DEFINITION_ID;

public class ParkedAccountsManager {

    private final TagInternalApi tagApi;

    @Inject
    public ParkedAccountsManager(final TagInternalApi tagApi) throws TagDefinitionApiException {
        this.tagApi = tagApi;
    }

    // Idempotent
    public void parkAccount(final UUID accountId, final InternalCallContext internalCallContext) throws TagApiException {
        try {
            tagApi.addTag(accountId, ObjectType.ACCOUNT, PARK_TAG_DEFINITION_ID, internalCallContext);
        } catch (final TagApiException e) {
            if (ErrorCode.TAG_ALREADY_EXISTS.getCode() != e.getCode()) {
                throw e;
            }
        }
    }

    public void unparkAccount(final UUID accountId, final InternalCallContext internalCallContext) throws TagApiException {
        tagApi.removeTag(accountId, ObjectType.ACCOUNT, PARK_TAG_DEFINITION_ID, internalCallContext);
    }

    public boolean isParked(final InternalCallContext internalCallContext) throws TagApiException {
        return Iterables.<Tag>tryFind(tagApi.getTagsForAccountType(ObjectType.ACCOUNT, false, internalCallContext),
                                      new Predicate<Tag>() {
                                          @Override
                                          public boolean apply(final Tag input) {
                                              return PARK_TAG_DEFINITION_ID.equals(input.getTagDefinitionId());
                                          }
                                      }).orNull() != null;
    }
}
