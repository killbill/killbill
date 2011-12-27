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

package com.ning.billing.analytics;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.ChangedField;
import com.ning.billing.analytics.dao.BusinessAccountDao;
import com.ning.billing.util.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BusinessAccountRecorder
{
    private static final Logger log = LoggerFactory.getLogger(BusinessAccountRecorder.class);

    private final BusinessAccountDao dao;
    private final AccountUserApi accountApi;

    @Inject
    public BusinessAccountRecorder(final BusinessAccountDao dao, final AccountUserApi accountApi)
    {
        this.dao = dao;
        this.accountApi = accountApi;
    }

    public void accountCreated(final AccountData data)
    {
        final Account account = accountApi.getAccountByKey(data.getExternalKey());

        final List<String> tags = new ArrayList<String>();
        for (final Tag tag : account.getTagList()) {
            tags.add(tag.getName());
        }

        // TODO Need payment and invoice api to fill most fields
        final BusinessAccount bac = new BusinessAccount(
            account.getExternalKey(),
            null, // TODO
            tags,
            null, // TODO
            null, // TODO
            null, // TODO
            null, // TODO
            null, // TODO
            null // TODO
        );

        log.info("ACCOUNT CREATION " + bac);
        dao.createAccount(bac);
    }

    public void accountUpdated(final UUID accountId, final List<ChangedField> changedFields)
    {
        // None of the fields updated interest us so far - see DefaultAccountChangeNotification
        // TODO We'll need notifications for tags changes eventually
    }
}
