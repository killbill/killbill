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
import com.ning.billing.account.api.IAccount;
import com.ning.billing.account.api.IAccountUserApi;
import com.ning.billing.analytics.dao.BusinessAccountDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BusinessAccountRecorder
{
    private static final Logger log = LoggerFactory.getLogger(BusinessAccountRecorder.class);

    private final BusinessAccountDao dao;
    private final IAccountUserApi accountApi;

    @Inject
    public BusinessAccountRecorder(final BusinessAccountDao dao, final IAccountUserApi accountApi)
    {
        this.dao = dao;
        this.accountApi = accountApi;
    }

    public void subscriptionCreated(final IAccount created)
    {
    }

    public void subscriptionUpdated(final IAccount updated)
    {
    }
}
