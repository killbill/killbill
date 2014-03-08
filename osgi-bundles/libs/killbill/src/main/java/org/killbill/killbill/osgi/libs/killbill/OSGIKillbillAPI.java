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

package org.killbill.killbill.osgi.libs.killbill;

import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.invoice.api.InvoicePaymentApi;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.osgi.api.OSGIKillbill;
import org.killbill.billing.osgi.api.config.PluginConfigServiceApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.ExportUserApi;
import org.killbill.billing.util.api.RecordIdApi;
import org.killbill.billing.util.api.TagUserApi;

public class OSGIKillbillAPI extends OSGIKillbillLibraryBase implements OSGIKillbill {


    private static final String KILLBILL_SERVICE_NAME = "org.killbill.billing.osgi.api.OSGIKillbill";

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
    public SubscriptionApi getSubscriptionApi() {
        return withServiceTracker(killbillTracker, new APICallback<SubscriptionApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public SubscriptionApi executeWithService(final OSGIKillbill service) {
                return service.getSubscriptionApi();
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
    public CurrencyConversionApi getCurrencyConversionApi() {
        return withServiceTracker(killbillTracker, new APICallback<CurrencyConversionApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public CurrencyConversionApi executeWithService(final OSGIKillbill service) {
                return service.getCurrencyConversionApi();
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
