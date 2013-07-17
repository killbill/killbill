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

package com.ning.billing.overdue.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;

import org.joda.time.Period;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.overdue.EmailNotification;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueCancellationPolicicy;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.util.config.catalog.ValidatingConfig;
import com.ning.billing.util.config.catalog.ValidationError;
import com.ning.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultOverdueState<T extends Blockable> extends ValidatingConfig<OverdueConfig> implements OverdueState<T> {

    private static final int MAX_NAME_LENGTH = 50;

    @XmlElement(required = false, name = "condition")
    private DefaultCondition<T> condition;

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
    private OverdueCancellationPolicicy subscriptionCancellationPolicy = OverdueCancellationPolicicy.NONE;

    @XmlElement(required = false, name = "isClearState")
    private Boolean isClearState = false;

    @XmlElement(required = false, name = "autoReevaluationInterval")
    private DefaultDuration autoReevaluationInterval;

    @XmlElement(required = false, name = "enterStateEmailNotification")
    private DefaultEmailNotification enterStateEmailNotification;

    //Other actions could include
    // - trigger payment retry?
    // - add tagStore to bundle/account
    // - set payment failure email template
    // - set payment retry interval
    // - backup payment mechanism?

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getExternalMessage() {
        return externalMessage;
    }

    @Override
    public boolean blockChanges() {
        return blockChanges || disableEntitlement;
    }

    @Override
    public boolean disableEntitlementAndChangesBlocked() {
        return disableEntitlement;
    }

    @Override
    public OverdueCancellationPolicicy getSubscriptionCancellationPolicy() {
        return subscriptionCancellationPolicy;
    }

    @Override
    public Period getReevaluationInterval() throws OverdueApiException {
        if (autoReevaluationInterval == null || autoReevaluationInterval.getUnit() == TimeUnit.UNLIMITED || autoReevaluationInterval.getNumber() == 0) {
            throw new OverdueApiException(ErrorCode.OVERDUE_NO_REEVALUATION_INTERVAL, name);
        }
        return autoReevaluationInterval.toJodaPeriod();
    }

    @Override
    public DefaultCondition<T> getCondition() {
        return condition;
    }

    protected DefaultOverdueState<T> setName(final String name) {
        this.name = name;
        return this;
    }

    protected DefaultOverdueState<T> setClearState(final boolean isClearState) {
        this.isClearState = isClearState;
        return this;
    }

    protected DefaultOverdueState<T> setExternalMessage(final String externalMessage) {
        this.externalMessage = externalMessage;
        return this;
    }

    protected DefaultOverdueState<T> setDisableEntitlement(final boolean cancel) {
        this.disableEntitlement = cancel;
        return this;
    }

    public DefaultOverdueState<T> setSubscriptionCancellationPolicy(final OverdueCancellationPolicicy policy) {
        this.subscriptionCancellationPolicy = policy;
        return this;
    }

    protected DefaultOverdueState<T> setBlockChanges(final boolean cancel) {
        this.blockChanges = cancel;
        return this;
    }

    protected DefaultOverdueState<T> setCondition(final DefaultCondition<T> condition) {
        this.condition = condition;
        return this;
    }

    @Override
    public boolean isClearState() {
        return isClearState;
    }

    @Override
    public ValidationErrors validate(final OverdueConfig root,
                                     final ValidationErrors errors) {
        if (name.length() > MAX_NAME_LENGTH) {
            errors.add(new ValidationError(String.format("Name of state '%s' exceeds the maximum length of %d", name, MAX_NAME_LENGTH), root.getURI(), DefaultOverdueState.class, name));
        }
        return errors;
    }

    @Override
    public int getDaysBetweenPaymentRetries() {
        return 8;
    }

    @Override
    public EmailNotification getEnterStateEmailNotification() {
        return enterStateEmailNotification;
    }
}
