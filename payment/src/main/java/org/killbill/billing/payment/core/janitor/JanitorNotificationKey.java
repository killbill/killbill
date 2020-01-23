/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.payment.core.janitor;

import java.util.UUID;

import org.killbill.notificationq.DefaultUUIDNotificationKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JanitorNotificationKey extends DefaultUUIDNotificationKey {

    private final String taskName;
    // Could be NULL (backward compatibility)
    private final Boolean isApiPayment;
    private final Integer attemptNumber;

    @JsonCreator
    public JanitorNotificationKey(@JsonProperty("uuidKey") final UUID uuidKey,
                                  @JsonProperty("taskName") final String taskName,
                                  @JsonProperty("apiPayment") final Boolean isApiPayment,
                                  @JsonProperty("attemptNumber") final Integer attemptNumber) {
        super(uuidKey);
        this.taskName = taskName;
        this.isApiPayment = isApiPayment;
        this.attemptNumber = attemptNumber;
    }

    public String getTaskName() {
        return taskName;
    }

    public Boolean getApiPayment() {
        return isApiPayment;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("JanitorNotificationKey{");
        sb.append("taskName='").append(taskName).append('\'');
        sb.append(", isApiPayment=").append(isApiPayment);
        sb.append(", attemptNumber=").append(attemptNumber);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final JanitorNotificationKey that = (JanitorNotificationKey) o;

        if (taskName != null ? !taskName.equals(that.taskName) : that.taskName != null) {
            return false;
        }
        if (isApiPayment != null ? !isApiPayment.equals(that.isApiPayment) : that.isApiPayment != null) {
            return false;
        }
        return attemptNumber != null ? attemptNumber.equals(that.attemptNumber) : that.attemptNumber == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (taskName != null ? taskName.hashCode() : 0);
        result = 31 * result + (isApiPayment != null ? isApiPayment.hashCode() : 0);
        result = 31 * result + (attemptNumber != null ? attemptNumber.hashCode() : 0);
        return result;
    }
}
