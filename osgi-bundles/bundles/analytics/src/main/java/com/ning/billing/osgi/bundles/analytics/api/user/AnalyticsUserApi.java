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

package com.ning.billing.osgi.bundles.analytics.api.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.analytics.api.TimeSeriesData;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.osgi.bundles.analytics.BusinessAccountDao;
import com.ning.billing.osgi.bundles.analytics.BusinessAnalyticsBase;
import com.ning.billing.osgi.bundles.analytics.BusinessInvoiceDao;
import com.ning.billing.osgi.bundles.analytics.BusinessInvoicePaymentDao;
import com.ning.billing.osgi.bundles.analytics.BusinessOverdueStatusDao;
import com.ning.billing.osgi.bundles.analytics.BusinessSubscriptionTransitionDao;
import com.ning.billing.osgi.bundles.analytics.BusinessTagDao;
import com.ning.billing.osgi.bundles.analytics.api.BusinessAccount;
import com.ning.billing.osgi.bundles.analytics.api.BusinessField;
import com.ning.billing.osgi.bundles.analytics.api.BusinessInvoice;
import com.ning.billing.osgi.bundles.analytics.api.BusinessInvoicePayment;
import com.ning.billing.osgi.bundles.analytics.api.BusinessOverdueStatus;
import com.ning.billing.osgi.bundles.analytics.api.BusinessSnapshot;
import com.ning.billing.osgi.bundles.analytics.api.BusinessSubscriptionTransition;
import com.ning.billing.osgi.bundles.analytics.api.BusinessTag;
import com.ning.billing.osgi.bundles.analytics.dao.AnalyticsDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessAccountTagModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessOverdueStatusModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.payment.PaymentInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

public class AnalyticsUserApi extends BusinessAnalyticsBase {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsUserApi.class);

    private final AnalyticsDao analyticsDao;
    private final BusinessSubscriptionTransitionDao bstDao;
    private final BusinessAccountDao bacDao;
    private final BusinessInvoiceDao invoiceDao;
    private final BusinessOverdueStatusDao bosDao;
    private final BusinessInvoicePaymentDao bipDao;
    private final BusinessTagDao tagDao;
    private final EntitlementInternalApi entitlementInternalApi;
    private final PaymentInternalApi paymentApi;
    private final TagInternalApi tagInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;

    public AnalyticsUserApi(final AnalyticsDao analyticsDao,
                            final BusinessSubscriptionTransitionDao bstDao,
                            final BusinessAccountDao bacDao,
                            final BusinessInvoiceDao invoiceDao,
                            final BusinessOverdueStatusDao bosDao,
                            final BusinessInvoicePaymentDao bipDao,
                            final BusinessTagDao tagDao,
                            final EntitlementInternalApi entitlementInternalApi,
                            final PaymentInternalApi paymentApi,
                            final TagInternalApi tagInternalApi,
                            final InternalCallContextFactory internalCallContextFactory) {
        super();

        this.analyticsDao = analyticsDao;
        this.bstDao = bstDao;
        this.bacDao = bacDao;
        this.invoiceDao = invoiceDao;
        this.bosDao = bosDao;
        this.bipDao = bipDao;
        this.tagDao = tagDao;
        this.entitlementInternalApi = entitlementInternalApi;
        this.paymentApi = paymentApi;
        this.tagInternalApi = tagInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public BusinessSnapshot getBusinessSnapshot(final Account account, final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);

        // Find account
        final BusinessAccount businessAccount = getAccountByKey(account.getExternalKey(), context);

        // Find all transitions for all bundles for that account, and associated overdue statuses
        final List<SubscriptionBundle> bundles = entitlementInternalApi.getBundlesForAccount(account.getId(), internalTenantContext);
        final Collection<BusinessSubscriptionTransition> businessSubscriptionTransitions = new ArrayList<BusinessSubscriptionTransition>();
        final Collection<BusinessOverdueStatus> businessOverdueStatuses = new ArrayList<BusinessOverdueStatus>();
        for (final SubscriptionBundle bundle : bundles) {
            businessSubscriptionTransitions.addAll(getTransitionsForBundle(bundle.getExternalKey(), context));
            businessOverdueStatuses.addAll(getOverdueStatusesForBundle(bundle.getExternalKey(), context));
        }

        // Find all invoices for that account
        final Collection<BusinessInvoice> businessInvoices = getInvoicesForAccount(account.getExternalKey(), context);

        // Find all payments for that account
        final Collection<BusinessInvoicePayment> businessInvoicePayments = getInvoicePaymentsForAccount(account.getExternalKey(), context);

        // Find all tags for that account
        // TODO add other tag types
        final Collection<BusinessTag> businessTags = getTagsForAccount(account.getExternalKey(), context);

        // TODO find custom fields
        final Collection<BusinessField> businessFields = ImmutableList.<BusinessField>of();

        return new BusinessSnapshot(businessAccount, businessSubscriptionTransitions, businessInvoices, businessInvoicePayments,
                                    businessOverdueStatuses, businessTags, businessFields);
    }

    public BusinessAccount getAccountByKey(final String accountKey, final TenantContext context) {
        final BusinessAccountModelDao accountByKey = analyticsDao.getAccountByKey(accountKey, internalCallContextFactory.createInternalTenantContext(context));
        if (accountByKey == null) {
            return null;
        } else {
            return new BusinessAccount(accountByKey);
        }
    }

    public List<BusinessSubscriptionTransition> getTransitionsForBundle(final String externalKey, final TenantContext context) {
        final List<BusinessSubscriptionTransitionModelDao> transitionsByKey = analyticsDao.getTransitionsByKey(externalKey, internalCallContextFactory.createInternalTenantContext(context));
        return ImmutableList.<BusinessSubscriptionTransition>copyOf(Collections2.transform(transitionsByKey, new Function<BusinessSubscriptionTransitionModelDao, BusinessSubscriptionTransition>() {

            public BusinessSubscriptionTransition apply(@Nullable final BusinessSubscriptionTransitionModelDao input) {
                return new BusinessSubscriptionTransition(input);
            }
        }));
    }

    public List<BusinessInvoice> getInvoicesForAccount(final String accountKey, final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        final List<BusinessInvoiceModelDao> invoicesByKey = analyticsDao.getInvoicesByKey(accountKey, internalTenantContext);
        return ImmutableList.<BusinessInvoice>copyOf(Collections2.transform(invoicesByKey, new Function<BusinessInvoiceModelDao, BusinessInvoice>() {

            public BusinessInvoice apply(@Nullable final BusinessInvoiceModelDao input) {
                return new BusinessInvoice(input, input == null ? null : analyticsDao.getInvoiceItemsForInvoice(input.getInvoiceId().toString(), internalTenantContext));
            }
        }));
    }

    public List<BusinessInvoicePayment> getInvoicePaymentsForAccount(final String accountKey, final TenantContext context) {
        final List<BusinessInvoicePaymentModelDao> invoicePaymentsForAccountByKey = analyticsDao.getInvoicePaymentsForAccountByKey(accountKey, internalCallContextFactory.createInternalTenantContext(context));
        return ImmutableList.<BusinessInvoicePayment>copyOf(Collections2.transform(invoicePaymentsForAccountByKey, new Function<BusinessInvoicePaymentModelDao, BusinessInvoicePayment>() {

            public BusinessInvoicePayment apply(@Nullable final BusinessInvoicePaymentModelDao input) {
                return new BusinessInvoicePayment(input);
            }
        }));
    }

    public List<BusinessOverdueStatus> getOverdueStatusesForBundle(final String externalKey, final TenantContext context) {
        final List<BusinessOverdueStatusModelDao> overdueStatusesForBundleByKey = analyticsDao.getOverdueStatusesForBundleByKey(externalKey, internalCallContextFactory.createInternalTenantContext(context));
        return ImmutableList.<BusinessOverdueStatus>copyOf(Collections2.transform(overdueStatusesForBundleByKey, new Function<BusinessOverdueStatusModelDao, BusinessOverdueStatus>() {

            public BusinessOverdueStatus apply(@Nullable final BusinessOverdueStatusModelDao input) {
                return new BusinessOverdueStatus(input);
            }
        }));
    }

    public List<BusinessTag> getTagsForAccount(final String accountKey, final TenantContext context) {
        final List<BusinessAccountTagModelDao> tagsForAccount = analyticsDao.getTagsForAccount(accountKey, internalCallContextFactory.createInternalTenantContext(context));
        return ImmutableList.<BusinessTag>copyOf(Collections2.transform(tagsForAccount, new Function<BusinessAccountTagModelDao, BusinessTag>() {

            public BusinessTag apply(@Nullable final BusinessAccountTagModelDao input) {
                return new BusinessTag(input);
            }
        }));
    }

    public TimeSeriesData getAccountsCreatedOverTime(final TenantContext context) {
        return analyticsDao.getAccountsCreatedOverTime(internalCallContextFactory.createInternalTenantContext(context));
    }

    public TimeSeriesData getSubscriptionsCreatedOverTime(final String productType, final String slug, final TenantContext context) {
        return analyticsDao.getSubscriptionsCreatedOverTime(productType, slug, internalCallContextFactory.createInternalTenantContext(context));
    }

    public void rebuildAnalyticsForAccount(final Account account, final CallContext context) {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);

        // Update the BAC row
        bacDao.accountUpdated(account.getId(), internalCallContext);

        // Update BST for all bundles
        final Set<UUID> bundleIds = updateBST(account, internalCallContext);

        // Update BIN and BII for all invoices
        invoiceDao.rebuildInvoicesForAccount(account.getId(), internalCallContext);

        // Update BIP for all invoices
        try {
            updateBIP(account, internalCallContext);
        } catch (PaymentApiException e) {
            // Log and ignore
            log.warn(e.toString());
        }

        // Update BOS for all bundles (only blockable supported today)
        // TODO: support other blockables
        for (final UUID bundleId : bundleIds) {
            bosDao.overdueStatusChanged(Type.SUBSCRIPTION_BUNDLE, bundleId, internalCallContext);
        }

        // Update bac_tags
        // TODO: refresh all tags
        updateTags(account, internalCallContext);
    }

    private Set<UUID> updateBST(final Account account, final InternalCallContext internalCallContext) {
        // Find the current state of bundles in entitlement
        final Collection<UUID> entitlementBundlesId = Collections2.transform(entitlementInternalApi.getBundlesForAccount(account.getId(), internalCallContext),
                                                                             new Function<SubscriptionBundle, UUID>() {

                                                                                 public UUID apply(@Nullable final SubscriptionBundle input) {
                                                                                     if (input == null) {
                                                                                         return null;
                                                                                     } else {
                                                                                         return input.getId();
                                                                                     }
                                                                                 }
                                                                             });

        // Find the current state of bundles in analytics
        final Collection<UUID> analyticsBundlesId = Collections2.transform(analyticsDao.getTransitionsForAccount(account.getExternalKey(), internalCallContext),
                                                                           new Function<BusinessSubscriptionTransitionModelDao, UUID>() {

                                                                               public UUID apply(@Nullable final BusinessSubscriptionTransitionModelDao input) {
                                                                                   if (input == null) {
                                                                                       return null;
                                                                                   } else {
                                                                                       return input.getBundleId();
                                                                                   }
                                                                               }
                                                                           });

        // Update BST for all bundles found
        final Set<UUID> bundlesId = new HashSet<UUID>();
        bundlesId.addAll(entitlementBundlesId);
        bundlesId.addAll(analyticsBundlesId);
        for (final UUID bundleId : bundlesId) {
            bstDao.update(bundleId, internalCallContext);
        }

        return bundlesId;
    }

    private void updateBIP(final Account account, final InternalCallContext internalCallContext) throws PaymentApiException {
        final List<Payment> accountPayments = paymentApi.getAccountPayments(account.getId(), internalCallContext);
        final Map<UUID, Payment> payments = new HashMap<UUID, Payment>();
        for (final Payment payment : accountPayments) {
            payments.put(payment.getId(), payment);
        }

        // Find the current state of payments in payment
        final Collection<UUID> paymentPaymentsId = Collections2.transform(accountPayments,
                                                                          new Function<Payment, UUID>() {

                                                                              public UUID apply(@Nullable final Payment input) {
                                                                                  if (input == null) {
                                                                                      return null;
                                                                                  } else {
                                                                                      return input.getId();
                                                                                  }
                                                                              }
                                                                          });

        // Find the current state of payments in analytics
        final Collection<UUID> analyticsPaymentsId = Collections2.transform(analyticsDao.getInvoicePaymentsForAccountByKey(account.getExternalKey(), internalCallContext),
                                                                            new Function<BusinessInvoicePaymentModelDao, UUID>() {

                                                                                public UUID apply(@Nullable final BusinessInvoicePaymentModelDao input) {
                                                                                    if (input == null) {
                                                                                        return null;
                                                                                    } else {
                                                                                        return input.getPaymentId();
                                                                                    }
                                                                                }
                                                                            });

        // Update BIP for all payments found
        final Set<UUID> paymentsId = new HashSet<UUID>();
        paymentsId.addAll(paymentPaymentsId);
        paymentsId.addAll(analyticsPaymentsId);
        for (final UUID paymentId : paymentsId) {
            final Payment paymentInfo = payments.get(paymentId);
            bipDao.invoicePaymentPosted(paymentInfo.getAccountId(),
                                        paymentInfo.getId(),
                                        paymentInfo.getPaymentStatus().toString(),
                                        internalCallContext);
        }
    }

    private void updateTags(final Account account, final InternalCallContext internalCallContext) {
        // Find the current state of tags from util
        final List<TagDefinition> tagDefinitions = tagInternalApi.getTagDefinitions(internalCallContext);
        final Collection<String> utilTags = Collections2.transform(tagInternalApi.getTags(account.getId(), ObjectType.ACCOUNT, internalCallContext),
                                                                   new Function<Tag, String>() {

                                                                       public String apply(@Nullable final Tag input) {
                                                                           if (input == null) {
                                                                               return "";
                                                                           }

                                                                           for (final TagDefinition tagDefinition : tagDefinitions) {
                                                                               if (tagDefinition.getId().equals(input.getTagDefinitionId())) {
                                                                                   return tagDefinition.getName();
                                                                               }
                                                                           }
                                                                           return "";
                                                                       }
                                                                   });

        // Find the current state of tags in analytics
        final Collection<String> analyticsTags = Collections2.transform(analyticsDao.getTagsForAccount(account.getExternalKey(), internalCallContext),
                                                                        new Function<BusinessAccountTagModelDao, String>() {

                                                                            public String apply(@Nullable final BusinessAccountTagModelDao input) {
                                                                                if (input == null) {
                                                                                    return null;
                                                                                } else {
                                                                                    return input.getName();
                                                                                }
                                                                            }
                                                                        });

        // Remove non existing tags
        for (final String tag : Sets.difference(new HashSet<String>(analyticsTags), new HashSet<String>(utilTags))) {
            tagDao.tagRemoved(ObjectType.ACCOUNT, account.getId(), tag, internalCallContext);
        }

        // Add missing ones
        for (final String tag : Sets.difference(new HashSet<String>(utilTags), new HashSet<String>(analyticsTags))) {
            tagDao.tagAdded(ObjectType.ACCOUNT, account.getId(), tag, internalCallContext);
        }
    }
}
