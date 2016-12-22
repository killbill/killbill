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

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.dao.TagDefinitionDao;
import org.killbill.billing.util.tag.dao.TagDefinitionModelDao;
import org.killbill.clock.Clock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class ParkedAccountsManager {

    @VisibleForTesting
    static final String PARK = "__PARK__";

    private final TagUserApi tagUserApi;
    private final TagDefinitionDao tagDefinitionDao;
    private final NonEntityDao nonEntityDao;
    private final CacheControllerDispatcher cacheControllerDispatcher;
    private /* final */ UUID tagDefinitionId;

    @Inject
    public ParkedAccountsManager(final TagUserApi tagUserApi,
                                 final TagDefinitionDao tagDefinitionDao,
                                 final NonEntityDao nonEntityDao,
                                 final CacheControllerDispatcher cacheControllerDispatcher,
                                 final Clock clock) throws TagDefinitionApiException {
        this.tagUserApi = tagUserApi;
        this.tagDefinitionDao = tagDefinitionDao;
        this.nonEntityDao = nonEntityDao;
        this.cacheControllerDispatcher = cacheControllerDispatcher;

        retrieveOrCreateParkTagDefinition(clock);
    }

    public void parkAccount(final UUID accountId, final InternalCallContext internalCallContext) throws TagApiException {
        final CallContext callContext = createCallContext(internalCallContext);
        tagUserApi.addTag(accountId, ObjectType.ACCOUNT, tagDefinitionId, callContext);
    }

    public void unparkAccount(final UUID accountId, final InternalCallContext internalCallContext) throws TagApiException {
        final CallContext callContext = createCallContext(internalCallContext);
        tagUserApi.removeTag(accountId, ObjectType.ACCOUNT, tagDefinitionId, callContext);
    }

    public boolean isParked(final UUID accountId, final InternalCallContext internalCallContext) throws TagApiException {
        final CallContext callContext = createCallContext(internalCallContext);
        return Iterables.<Tag>tryFind(tagUserApi.getTagsForAccount(accountId, false, callContext),
                                      new Predicate<Tag>() {
                                          @Override
                                          public boolean apply(final Tag input) {
                                              return tagDefinitionId.equals(input.getTagDefinitionId());
                                          }
                                      }).orNull() != null;
    }

    // TODO Consider creating a tag internal API to avoid this
    private CallContext createCallContext(final InternalCallContext internalCallContext) {
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(internalCallContext.getTenantRecordId(),
                                                                ObjectType.TENANT,
                                                                cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID));
        return internalCallContext.toCallContext(tenantId);
    }

    @VisibleForTesting
    void retrieveOrCreateParkTagDefinition(final Clock clock) throws TagDefinitionApiException {
        final InternalCallContext callContext = new InternalCallContext(InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID,
                                                                        null,
                                                                        null,
                                                                        null,
                                                                        UUID.randomUUID(),
                                                                        ParkedAccountsManager.class.getName(),
                                                                        CallOrigin.INTERNAL,
                                                                        UserType.SYSTEM,
                                                                        null,
                                                                        null,
                                                                        clock.getUTCNow(),
                                                                        clock.getUTCNow());
        // Need to use the DAO directly to bypass validations
        TagDefinitionModelDao tagDefinitionModelDao = tagDefinitionDao.getByName(PARK, callContext);
        if (tagDefinitionModelDao == null) {
            tagDefinitionModelDao = tagDefinitionDao.create(PARK, "Accounts with invalid invoicing state", callContext);
        }
        this.tagDefinitionId = tagDefinitionModelDao.getId();
    }
}
