package org.killbill.billing.catalog;

import org.killbill.billing.catalog.api.BillingPeriod;

public class MockRecurring extends DefaultRecurring {

    public MockRecurring(final BillingPeriod billingPeriod,
                         final DefaultInternationalPrice recurringPrice) {
        setBillingPeriod(billingPeriod);
        setRecurringPrice(recurringPrice);
    }

}
