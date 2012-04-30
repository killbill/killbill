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

package com.ning.billing.junction.plumbing.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.block.BlockingChecker;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;


public class BlockingSubscription implements Subscription {
    private final Subscription subscription;
    private final BlockingApi blockingApi; 
    private final BlockingChecker checker;
    
    private BlockingState blockingState = null;

    public BlockingSubscription(Subscription subscription, BlockingApi blockingApi, BlockingChecker checker) {
        this.subscription = subscription;
        this.blockingApi = blockingApi;
        this.checker = checker;
    }

    public List<Tag> getTagList() {
        return subscription.getTagList();
    }

    public UUID getId() {
        return subscription.getId();
    }

    public String getCreatedBy() {
        return subscription.getCreatedBy();
    }

    public boolean hasTag(String tagName) {
        return subscription.hasTag(tagName);
    }

    public DateTime getCreatedDate() {
        return subscription.getCreatedDate();
    }

    public void addTag(TagDefinition definition) {
        subscription.addTag(definition);
    }

    public String getFieldValue(String fieldName) {
        return subscription.getFieldValue(fieldName);
    }

    public void addTags(List<Tag> tags) {
        subscription.addTags(tags);
    }

    public void setFieldValue(String fieldName, String fieldValue) {
        subscription.setFieldValue(fieldName, fieldValue);
    }

    public void clearTags() {
        subscription.clearTags();
    }

    public void removeTag(TagDefinition definition) {
        subscription.removeTag(definition);
    }

    public void saveFieldValue(String fieldName, String fieldValue, CallContext context) {
        subscription.saveFieldValue(fieldName, fieldValue, context);
    }

    public boolean generateInvoice() {
        return subscription.generateInvoice();
    }

    public boolean processPayment() {
        return subscription.processPayment();
    }

    public List<CustomField> getFieldList() {
        return subscription.getFieldList();
    }

    public void setFields(List<CustomField> fields) {
        subscription.setFields(fields);
    }

    public void saveFields(List<CustomField> fields, CallContext context) {
        subscription.saveFields(fields, context);
    }

    public void clearFields() {
        subscription.clearFields();
    }

    public void clearPersistedFields(CallContext context) {
        subscription.clearPersistedFields(context);
    }

    public String getObjectName() {
        return subscription.getObjectName();
    }

    public boolean cancel(DateTime requestedDate, boolean eot, CallContext context) throws EntitlementUserApiException {
        return subscription.cancel(requestedDate, eot, context);
    }

    public boolean uncancel(CallContext context) throws EntitlementUserApiException {
        return subscription.uncancel(context);
    }

    public boolean changePlan(String productName, BillingPeriod term, String planSet, DateTime requestedDate,
            CallContext context) throws EntitlementUserApiException {
        try {
            checker.checkBlockedChange(this);
        } catch (BlockingApiException e) {
            throw new EntitlementUserApiException(e, e.getCode(), e.getMessage()); 
        }
        return subscription.changePlan(productName, term, planSet, requestedDate, context);
    }

    public boolean recreate(PlanPhaseSpecifier spec, DateTime requestedDate, CallContext context)
            throws EntitlementUserApiException {
        return subscription.recreate(spec, requestedDate, context);
    }

    public UUID getBundleId() {
        return subscription.getBundleId();
    }

    public SubscriptionState getState() {
        return subscription.getState();
    }

    public DateTime getStartDate() {
        return subscription.getStartDate();
    }

    public DateTime getEndDate() {
        return subscription.getEndDate();
    }

    public Plan getCurrentPlan() {
        return subscription.getCurrentPlan();
    }

    public PriceList getCurrentPriceList() {
        return subscription.getCurrentPriceList();
    }

    public PlanPhase getCurrentPhase() {
        return subscription.getCurrentPhase();
    }

    public DateTime getChargedThroughDate() {
        return subscription.getChargedThroughDate();
    }

    public DateTime getPaidThroughDate() {
        return subscription.getPaidThroughDate();
    }

    public ProductCategory getCategory() {
        return subscription.getCategory();
    }

    public SubscriptionEventTransition getPendingTransition() {
        return subscription.getPendingTransition();
    }

    public SubscriptionEventTransition getPreviousTransition() {
        return subscription.getPreviousTransition();
    }

    public List<SubscriptionEventTransition> getBillingTransitions() {
        return subscription.getBillingTransitions();
    }

    public BlockingState getBlockingState() {
        if(blockingState == null) {
            blockingState = blockingApi.getBlockingStateFor(this);
        }
        return blockingState;
    }
    
    public Subscription getDelegateSubscription() {
        return subscription;
    }


}
