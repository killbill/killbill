/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.export.api;

import java.io.OutputStream;
import java.util.UUID;

import javax.inject.Inject;

import com.ning.billing.util.api.DatabaseExportOutputStream;
import com.ning.billing.util.api.ExportUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.export.dao.CSVExportOutputStream;
import com.ning.billing.util.export.dao.DatabaseExportDao;

public class DefaultExportUserApi implements ExportUserApi {

    private final DatabaseExportDao exportDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultExportUserApi(final DatabaseExportDao exportDao,
                                final InternalCallContextFactory internalCallContextFactory) {
        this.exportDao = exportDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void exportDataForAccount(final UUID accountId, final DatabaseExportOutputStream out, final CallContext context) {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(accountId, context);
        exportDao.exportDataForAccount(out, internalContext);
    }

    @Override
    public void exportDataAsCSVForAccount(final UUID accountId, final OutputStream out, final CallContext context) {
        exportDataForAccount(accountId, new CSVExportOutputStream(out), context);
    }
}
