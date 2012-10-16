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

package com.ning.billing.util.api;

import java.io.OutputStream;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;

// Although it's a read-only call, we want to know who triggered the export - hence the call context here
public interface ExportUserApi {

    public void exportDataForAccount(UUID accountId, DatabaseExportOutputStream out, CallContext context);

    public void exportDataAsCSVForAccount(UUID accountId, OutputStream out, CallContext context);
}
