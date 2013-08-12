package com.ning.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.Entitlement.EntitlementSourceType;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.util.callcontext.InternalTenantContext;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;


public class TestDefaultEntitlementApi extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void test1() {

        try {
            final LocalDate initialDate = new LocalDate(2013, 8, 7);
            clock.setDay(initialDate);
            final UUID accountId = UUID.randomUUID();
            final String externalKey = "externalKey";
            final Account account = Mockito.mock(Account.class);
            Mockito.when(account.getId()).thenReturn(accountId);
            Mockito.when(accountInternalApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

            final Entitlement entitlement = entitlementApi.createBaseEntitlement(accountId, spec, externalKey, callContext);
            assertEquals(entitlement.getAccountId(), accountId);
            assertEquals(entitlement.getExternalKey(), externalKey);

            assertEquals(entitlement.getRequestedEndDate(), initialDate);
            assertEquals(entitlement.getEffectiveStartDate(), initialDate);
            assertNull(entitlement.getEffectiveStartDate());

            assertEquals(entitlement.getPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(entitlement.getProduct().getName(), "Shotgun");
            assertEquals(entitlement.getCurrentPhase().getName(), "shotgun-monthly-trial");
            assertEquals(entitlement.getPlan().getName(), "shotgun-monthly");
            assertEquals(entitlement.getProductCategory(), ProductCategory.BASE);

            assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
            assertEquals(entitlement.getSourceType(), EntitlementSourceType.NATIVE);

            assertEquals(entitlement.getLastActivePlan(), null);
            assertEquals(entitlement.getLastActivePriceList(), null);
            assertEquals(entitlement.getLastActiveProduct(), null);
            assertEquals(entitlement.getLastActiveProductCategory(), null);

        } catch (EntitlementApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        } catch (AccountApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        }
    }
}
