/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.util.audit;

import org.joda.time.DateTime;

import com.ning.billing.util.entity.Entity;


public interface AuditLog extends Entity {

    /**
     * Get the type of change for this log entry
     *
     * @return the ChangeType
     */
    public ChangeType getChangeType();

    /**
     * Get the name of the requestor
     *
     * @return the requestor user name
     */
    public String getUserName();

    /**
     * Get the time when this change was effective
     *
     * @return the created date of this log entry
     */
    public DateTime getCreatedDate();

    /**
     * Get the reason code for this change
     *
     * @return the reason code
     */
    public String getReasonCode();

    /**
     * Get the user token of this change requestor
     *
     * @return the user token
     */
    public String getUserToken();

    /**
     * Get the comment for this change
     *
     * @return the comment
     */
    public String getComment();
}
