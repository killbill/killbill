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

package com.ning.killbill.osgi.libs.killbill;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.CatalogUserApi;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.entitlement.api.EntitlementApi;
import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.subscription.api.timeline.SubscriptionTimelineApi;
import com.ning.billing.subscription.api.transfer.SubscriptionTransferApi;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.tenant.api.TenantUserApi;
import com.ning.billing.usage.api.UsageUserApi;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.ExportUserApi;
import com.ning.billing.util.api.RecordIdApi;
import com.ning.billing.util.api.TagUserApi;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class OSGIKillbillAPI extends OSGIKillbillLibraryBase implements OSGIKillbill {


    private static final String KILLBILL_SERVICE_NAME = "com.ning.billing.osgi.api.OSGIKillbill";

    private final ServiceTracker<OSGIKillbill, OSGIKillbill> killbillTracker;

    public OSGIKillbillAPI(BundleContext context) {
        killbillTracker = new ServiceTracker(context, KILLBILL_SERVICE_NAME, null);
        killbillTracker.open();
    }

    public void close() {
        if (killbillTracker != null) {
            killbillTracker.close();
        }
    }

    @Override
    public AccountUserApi getAccountUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<AccountUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public AccountUserApi executeWithService(final OSGIKillbill service) {
                return service.getAccountUserApi();
            }
        });
    }

    @Override
    public CatalogUserApi getCatalogUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<CatalogUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public CatalogUserApi executeWithService(final OSGIKillbill service) {
                return service.getCatalogUserApi();
            }
        });
    }

    @Override
    public SubscriptionTimelineApi getSubscriptionTimelineApi() {
        return withServiceTracker(killbillTracker, new APICallback<SubscriptionTimelineApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public SubscriptionTimelineApi executeWithService(final OSGIKillbill service) {
                return service.getSubscriptionTimelineApi();
            }
        });
    }

    @Override
    public SubscriptionTransferApi getSubscriptionTransferApi() {
        return withServiceTracker(killbillTracker, new APICallback<SubscriptionTransferApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public SubscriptionTransferApi executeWithService(final OSGIKillbill service) {
                return service.getSubscriptionTransferApi();
            }
        });
    }

    @Override
    public SubscriptionUserApi getSubscriptionUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<SubscriptionUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public SubscriptionUserApi executeWithService(final OSGIKillbill service) {
                return service.getSubscriptionUserApi();
            }
        });
    }

    @Override
    public InvoicePaymentApi getInvoicePaymentApi() {
        return withServiceTracker(killbillTracker, new APICallback<InvoicePaymentApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public InvoicePaymentApi executeWithService(final OSGIKillbill service) {
                return service.getInvoicePaymentApi();
            }
        });
    }

    @Override
    public InvoiceUserApi getInvoiceUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<InvoiceUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public InvoiceUserApi executeWithService(final OSGIKillbill service) {
                return service.getInvoiceUserApi();
            }
        });
    }

    @Override
    public OverdueUserApi getOverdueUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<OverdueUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public OverdueUserApi executeWithService(final OSGIKillbill service) {
                return service.getOverdueUserApi();
            }
        });
    }

    @Override
    public PaymentApi getPaymentApi() {
        return withServiceTracker(killbillTracker, new APICallback<PaymentApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public PaymentApi executeWithService(final OSGIKillbill service) {
                return service.getPaymentApi();
            }
        });
    }

    @Override
    public TenantUserApi getTenantUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<TenantUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public TenantUserApi executeWithService(final OSGIKillbill service) {
                return service.getTenantUserApi();
            }
        });
    }

    @Override
    public UsageUserApi getUsageUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<UsageUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public UsageUserApi executeWithService(final OSGIKillbill service) {
                return service.getUsageUserApi();
            }
        });
    }

    @Override
    public AuditUserApi getAuditUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<AuditUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public AuditUserApi executeWithService(final OSGIKillbill service) {
                return service.getAuditUserApi();
            }
        });
    }

    @Override
    public CustomFieldUserApi getCustomFieldUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<CustomFieldUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public CustomFieldUserApi executeWithService(final OSGIKillbill service) {
                return service.getCustomFieldUserApi();
            }
        });
    }

    @Override
    public ExportUserApi getExportUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<ExportUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public ExportUserApi executeWithService(final OSGIKillbill service) {
                return service.getExportUserApi();
            }
        });
    }

    @Override
    public TagUserApi getTagUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<TagUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public TagUserApi executeWithService(final OSGIKillbill service) {
                return service.getTagUserApi();
            }
        });
    }

    @Override
    public EntitlementApi getEntitlementApi() {
        return withServiceTracker(killbillTracker, new APICallback<EntitlementApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public EntitlementApi executeWithService(final OSGIKillbill service) {
                return service.getEntitlementApi();
            }
        });
    }

    @Override
    public RecordIdApi getRecordIdApi() {
        return withServiceTracker(killbillTracker, new APICallback<RecordIdApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public RecordIdApi executeWithService(final OSGIKillbill service) {
                return service.getRecordIdApi();
            }
        });
    }

    @Override
    public PluginConfigServiceApi getPluginConfigServiceApi() {
        return withServiceTracker(killbillTracker, new APICallback<PluginConfigServiceApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public PluginConfigServiceApi executeWithService(final OSGIKillbill service) {
                return service.getPluginConfigServiceApi();
            }
        });
    }
}
