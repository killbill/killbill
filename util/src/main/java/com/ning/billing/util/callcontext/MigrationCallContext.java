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

package com.ning.billing.util.callcontext;

import org.joda.time.DateTime;

public class MigrationCallContext extends CallContextBase {
    private final DateTime createdDate;
    private final DateTime updatedDate;

    public MigrationCallContext(final String userName, final CallOrigin callOrigin, final UserType userType, final DateTime createdDate, final DateTime updatedDate) {
        super(userName, callOrigin, userType);
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public MigrationCallContext(final CallContext context, final DateTime createdDate, final DateTime updatedDate) {
        super(context.getUserName(), context.getCallOrigin(), context.getUserType());
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }
}
