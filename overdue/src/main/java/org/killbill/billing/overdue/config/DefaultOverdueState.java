/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.overdue.config;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;

import org.joda.time.Period;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.overdue.ConditionEvaluation;
import org.killbill.billing.overdue.api.EmailNotification;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueCancellationPolicy;
import org.killbill.billing.overdue.api.OverdueCondition;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultOverdueState extends ValidatingConfig<DefaultOverdueConfig> implements OverdueState {

    private static final int MAX_NAME_LENGTH = 50;

    @XmlElement(required = false, name = "condition")
    private DefaultOverdueCondition condition;

    @XmlAttribute(required = true, name = "name")
    @XmlID
    private String name;

    @XmlElement(required = false, name = "externalMessage")
    private String externalMessage = "";

    @XmlElement(required = false, name = "blockChanges")
    private Boolean blockChanges = false;

    @XmlElement(required = false, name = "disableEntitlementAndChangesBlocked")
    private Boolean disableEntitlement = false;

    @XmlElement(required = false, name = "subscriptionCancellationPolicy")
    private OverdueCancellationPolicy subscriptionCancellationPolicy = OverdueCancellationPolicy.NONE;

    @XmlElement(required = false, name = "isClearState")
    private Boolean isClearState = false;

    @XmlElement(required = false, name = "autoReevaluationInterval")
    private DefaultDuration autoReevaluationInterval;

    @Deprecated // Not used, just kept for config compatibility
    @XmlElement(required = false, name = "enterStateEmailNotification")
    private DefaultEmailNotification enterStateEmailNotification;

    //Other actions could include
    // - trigger payment retry?
    // - add tagStore to bundle/account
    // - set payment failure email template
    // - set payment retry interval
    // - backup payment mechanism?

    public ConditionEvaluation getConditionEvaluation() {
        return condition;
    }

    @Override
    public OverdueCondition getOverdueCondition() {
        return condition;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getExternalMessage() {
        return externalMessage;
    }

    @Override
    public boolean isBlockChanges() {
        return blockChanges;
    }

    @Override
    public boolean isDisableEntitlementAndChangesBlocked() {
        return disableEntitlement;
    }

    @Override
    public OverdueCancellationPolicy getOverdueCancellationPolicy() {
        return subscriptionCancellationPolicy;
    }

    @Override
    public Duration getAutoReevaluationInterval() throws OverdueApiException {
        if (autoReevaluationInterval == null || autoReevaluationInterval.getUnit() == TimeUnit.UNLIMITED || autoReevaluationInterval.getNumber() == 0) {
            throw new OverdueApiException(ErrorCode.OVERDUE_NO_REEVALUATION_INTERVAL, name);
        }
        return autoReevaluationInterval;
    }

    public void setAutoReevaluationInterval(final DefaultDuration autoReevaluationInterval) {
        this.autoReevaluationInterval = autoReevaluationInterval;
    }

    public DefaultOverdueState setName(final String name) {
        this.name = name;
        return this;
    }

    public DefaultOverdueState setClearState(final boolean isClearState) {
        this.isClearState = isClearState;
        return this;
    }

    public DefaultOverdueState setExternalMessage(final String externalMessage) {
        this.externalMessage = externalMessage;
        return this;
    }

    public DefaultOverdueState setDisableEntitlement(final boolean cancel) {
        this.disableEntitlement = cancel;
        return this;
    }

    public DefaultOverdueState setSubscriptionCancellationPolicy(final OverdueCancellationPolicy policy) {
        this.subscriptionCancellationPolicy = policy;
        return this;
    }

    public DefaultOverdueState setBlockChanges(final boolean cancel) {
        this.blockChanges = cancel;
        return this;
    }

    public DefaultOverdueState setCondition(final DefaultOverdueCondition condition) {
        this.condition = condition;
        return this;
    }

    @Override
    public boolean isClearState() {
        return isClearState;
    }


    @Override
    public ValidationErrors validate(final DefaultOverdueConfig root,
                                     final ValidationErrors errors) {
        if (name.length() > MAX_NAME_LENGTH) {
            errors.add(new ValidationError(String.format("Name of state '%s' exceeds the maximum length of %d", name, MAX_NAME_LENGTH), root.getURI(), DefaultOverdueState.class, name));
        }
        return errors;
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultOverdueState{");
        sb.append("condition=").append(condition);
        sb.append(", name='").append(name).append('\'');
        sb.append(", externalMessage='").append(externalMessage).append('\'');
        sb.append(", blockChanges=").append(blockChanges);
        sb.append(", disableEntitlement=").append(disableEntitlement);
        sb.append(", subscriptionCancellationPolicy=").append(subscriptionCancellationPolicy);
        sb.append(", isClearState=").append(isClearState);
        sb.append(", autoReevaluationInterval=").append(autoReevaluationInterval);
        sb.append('}');
        return sb.toString();
    }
}
