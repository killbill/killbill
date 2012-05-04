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

package com.ning.billing.junction.plumbing.billing;

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
import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;


public class MockSubscription implements Subscription {
    Subscription sub = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);

    public List<Tag> getTagList() {
        return sub.getTagList();
    }

    public UUID getId() {
        return sub.getId();
    }

    public String getCreatedBy() {
        return sub.getCreatedBy();
    }

    public boolean hasTag(TagDefinition tagDefinition) {
        return sub.hasTag(tagDefinition);
    }

    public DateTime getCreatedDate() {
        return sub.getCreatedDate();
    }

    public String getFieldValue(String fieldName) {
        return sub.getFieldValue(fieldName);
    }

    public boolean hasTag(ControlTagType controlTagType) {
        return sub.hasTag(controlTagType);
    }

    public void setFieldValue(String fieldName, String fieldValue) {
        sub.setFieldValue(fieldName, fieldValue);
    }

    public void addTag(TagDefinition definition) {
        sub.addTag(definition);
    }

    public void addTags(List<Tag> tags) {
        sub.addTags(tags);
    }

    public void saveFieldValue(String fieldName, String fieldValue, CallContext context) {
        sub.saveFieldValue(fieldName, fieldValue, context);
    }

    public void addTagsFromDefinitions(List<TagDefinition> tagDefinitions) {
        sub.addTagsFromDefinitions(tagDefinitions);
    }

    public List<CustomField> getFieldList() {
        return sub.getFieldList();
    }

    public void clearTags() {
        sub.clearTags();
    }

    public void setFields(List<CustomField> fields) {
        sub.setFields(fields);
    }

    public void removeTag(TagDefinition definition) {
        sub.removeTag(definition);
    }

    public void saveFields(List<CustomField> fields, CallContext context) {
        sub.saveFields(fields, context);
    }

    public boolean generateInvoice() {
        return sub.generateInvoice();
    }

    public boolean processPayment() {
        return sub.processPayment();
    }

    public void clearFields() {
        sub.clearFields();
    }

    public void clearPersistedFields(CallContext context) {
        sub.clearPersistedFields(context);
    }

    public boolean cancel(DateTime requestedDate, boolean eot, CallContext context) throws EntitlementUserApiException {
        return sub.cancel(requestedDate, eot, context);
    }

    public String getObjectName() {
        return sub.getObjectName();
    }

    public boolean uncancel(CallContext context) throws EntitlementUserApiException {
        return sub.uncancel(context);
    }

    public boolean changePlan(String productName, BillingPeriod term, String planSet, DateTime requestedDate,
            CallContext context) throws EntitlementUserApiException {
        return sub.changePlan(productName, term, planSet, requestedDate, context);
    }

    public boolean recreate(PlanPhaseSpecifier spec, DateTime requestedDate, CallContext context)
            throws EntitlementUserApiException {
        return sub.recreate(spec, requestedDate, context);
    }

    public UUID getBundleId() {
        return sub.getBundleId();
    }

    public SubscriptionState getState() {
        return sub.getState();
    }

    public DateTime getStartDate() {
        return sub.getStartDate();
    }

    public DateTime getEndDate() {
        return sub.getEndDate();
    }

    public Plan getCurrentPlan() {
        return sub.getCurrentPlan();
    }

    public BlockingState getBlockingState() {
        return sub.getBlockingState();
    }

    public PriceList getCurrentPriceList() {
        return sub.getCurrentPriceList();
    }

    public PlanPhase getCurrentPhase() {
        return sub.getCurrentPhase();
    }

    public DateTime getChargedThroughDate() {
        return sub.getChargedThroughDate();
    }

    public DateTime getPaidThroughDate() {
        return sub.getPaidThroughDate();
    }

    public ProductCategory getCategory() {
        return sub.getCategory();
    }

    public SubscriptionEvent getPendingTransition() {
        return sub.getPendingTransition();
    }

    public SubscriptionEvent getPreviousTransition() {
        return sub.getPreviousTransition();
    }

    public List<SubscriptionEvent> getBillingTransitions() {
        return sub.getBillingTransitions();
    }
    
    
}
