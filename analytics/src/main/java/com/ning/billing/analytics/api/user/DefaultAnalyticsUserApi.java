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

package com.ning.billing.analytics.api.user;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.analytics.BusinessAccountDao;
import com.ning.billing.analytics.BusinessInvoiceDao;
import com.ning.billing.analytics.BusinessInvoicePaymentDao;
import com.ning.billing.analytics.BusinessOverdueStatusDao;
import com.ning.billing.analytics.BusinessSubscriptionTransitionDao;
import com.ning.billing.analytics.BusinessTagDao;
import com.ning.billing.analytics.api.TimeSeriesData;
import com.ning.billing.analytics.dao.AnalyticsDao;
import com.ning.billing.analytics.model.BusinessAccountModelDao;
import com.ning.billing.analytics.model.BusinessAccountTagModelDao;
import com.ning.billing.analytics.model.BusinessInvoiceModelDao;
import com.ning.billing.analytics.model.BusinessInvoiceItemModelDao;
import com.ning.billing.analytics.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.analytics.model.BusinessOverdueStatusModelDao;
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

public class DefaultAnalyticsUserApi implements AnalyticsUserApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultAnalyticsUserApi.class);

    private final AnalyticsDao analyticsDao;
    private final BusinessSubscriptionTransitionDao bstDao;
    private final BusinessAccountDao bacDao;
    private final BusinessInvoiceDao invoiceDao;
    private final BusinessOverdueStatusDao bosDao;
    private final BusinessInvoicePaymentDao bipDao;
    private final BusinessTagDao tagDao;
    private final EntitlementInternalApi entitlementInternalApi;
    private final PaymentApi paymentApi;
    private final TagInternalApi tagInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultAnalyticsUserApi(final AnalyticsDao analyticsDao,
                                   final BusinessSubscriptionTransitionDao bstDao,
                                   final BusinessAccountDao bacDao,
                                   final BusinessInvoiceDao invoiceDao,
                                   final BusinessOverdueStatusDao bosDao,
                                   final BusinessInvoicePaymentDao bipDao,
                                   final BusinessTagDao tagDao,
                                   final EntitlementInternalApi entitlementInternalApi,
                                   final PaymentApi paymentApi,
                                   final TagInternalApi tagInternalApi,
                                   final InternalCallContextFactory internalCallContextFactory) {
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

    @Override
    public TimeSeriesData getAccountsCreatedOverTime(final TenantContext context) {
        return analyticsDao.getAccountsCreatedOverTime(internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public TimeSeriesData getSubscriptionsCreatedOverTime(final String productType, final String slug, final TenantContext context) {
        return analyticsDao.getSubscriptionsCreatedOverTime(productType, slug, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public void rebuildAnalyticsForAccount(final Account account, final CallContext context) {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(context);

        // Update the BAC row
        bacDao.accountUpdated(account.getId(), internalCallContext);

        // Update BST for all bundles
        final Set<UUID> bundleIds = updateBST(account, internalCallContext);

        // Update BIN and BII for all invoices
        invoiceDao.rebuildInvoicesForAccount(account.getId(), internalCallContext);

        // Update BIP for all invoices
        try {
            updateBIP(account, context, internalCallContext);
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
                                                                                 @Override
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
                                                                               @Override
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
            bstDao.rebuildTransitionsForBundle(bundleId, internalCallContext);
        }

        return bundlesId;
    }

    private void updateBIP(final Account account, final TenantContext tenantContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final List<Payment> accountPayments = paymentApi.getAccountPayments(account.getId(), tenantContext);
        final Map<UUID, Payment> payments = new HashMap<UUID, Payment>();
        for (final Payment payment : accountPayments) {
            payments.put(payment.getId(), payment);
        }

        // Find the current state of payments in payment
        final Collection<UUID> paymentPaymentsId = Collections2.transform(accountPayments,
                                                                          new Function<Payment, UUID>() {
                                                                              @Override
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
                                                                                @Override
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
                                        paymentInfo.getExtFirstPaymentIdRef(),
                                        paymentInfo.getExtSecondPaymentIdRef(),
                                        paymentInfo.getPaymentStatus().toString(),
                                        internalCallContext);
        }
    }

    private void updateTags(final Account account, final InternalCallContext internalCallContext) {
        // Find the current state of tags from util
        final List<TagDefinition> tagDefinitions = tagInternalApi.getTagDefinitions(internalCallContext);
        final Collection<String> utilTags = Collections2.transform(tagInternalApi.getTags(account.getId(), ObjectType.ACCOUNT, internalCallContext).values(),
                                                                   new Function<Tag, String>() {
                                                                       @Override
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
                                                                            @Override
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

    // Note: the following is not exposed in api yet, as the models need to be extracted first

    public BusinessAccountModelDao getAccountByKey(final String accountKey, final TenantContext context) {
        return analyticsDao.getAccountByKey(accountKey, internalCallContextFactory.createInternalTenantContext(context));
    }

    public List<BusinessSubscriptionTransitionModelDao> getTransitionsForBundle(final String externalKey, final TenantContext context) {
        return analyticsDao.getTransitionsByKey(externalKey, internalCallContextFactory.createInternalTenantContext(context));
    }

    public List<BusinessInvoiceModelDao> getInvoicesForAccount(final String accountKey, final TenantContext context) {
        return analyticsDao.getInvoicesByKey(accountKey, internalCallContextFactory.createInternalTenantContext(context));
    }

    public List<BusinessAccountTagModelDao> getTagsForAccount(final String accountKey, final TenantContext context) {
        return analyticsDao.getTagsForAccount(accountKey, internalCallContextFactory.createInternalTenantContext(context));
    }

    public List<BusinessOverdueStatusModelDao> getOverdueStatusesForBundle(final String externalKey, final TenantContext context) {
        return analyticsDao.getOverdueStatusesForBundleByKey(externalKey, internalCallContextFactory.createInternalTenantContext(context));
    }

    public List<BusinessInvoiceItemModelDao> getInvoiceItemsForInvoice(final UUID invoiceId, final TenantContext context) {
        return analyticsDao.getInvoiceItemsForInvoice(invoiceId.toString(), internalCallContextFactory.createInternalTenantContext(context));
    }

    public List<BusinessInvoicePaymentModelDao> getInvoicePaymentsForAccount(final String accountKey, final TenantContext context) {
        return analyticsDao.getInvoicePaymentsForAccountByKey(accountKey, internalCallContextFactory.createInternalTenantContext(context));
    }
}
