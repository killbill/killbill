package com.ning.billing.entitlement.api;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.Entitlement.EntitlementSourceType;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;


public class TestDefaultEntitlementApi extends EntitlementTestSuiteWithEmbeddedDB {


    @Test(groups = "slow")
    public void testCreateEntitlementWithCheck() {

        try {


            final LocalDate initialDate = new LocalDate(2013, 8, 7);
            clock.setDay(initialDate);

            final Account account = accountApi.createAccount(getAccountData(7), callContext);

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

            // Create entitlement and check each field
            final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
            assertEquals(entitlement.getAccountId(), account.getId());
            assertEquals(entitlement.getExternalKey(), account.getExternalKey());

            assertEquals(entitlement.getEffectiveStartDate(), initialDate);
            assertNull(entitlement.getEffectiveEndDate());

            assertEquals(entitlement.getPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(entitlement.getProduct().getName(), "Shotgun");
            assertEquals(entitlement.getCurrentPhase().getName(), "shotgun-monthly-trial");
            assertEquals(entitlement.getPlan().getName(), "shotgun-monthly");
            assertEquals(entitlement.getProductCategory(), ProductCategory.BASE);

            assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
            assertEquals(entitlement.getSourceType(), EntitlementSourceType.NATIVE);

            assertEquals(entitlement.getLastActivePlan().getName(), "shotgun-monthly");
            assertEquals(entitlement.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(entitlement.getLastActiveProduct().getName(), "Shotgun");
            assertEquals(entitlement.getLastActiveProductCategory(), ProductCategory.BASE);

            assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
            assertEquals(entitlement.getSourceType(), EntitlementSourceType.NATIVE);

            // Now retrieve entitlement by id and recheck everything
            final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);

            assertEquals(entitlement2.getAccountId(), account.getId());
            assertEquals(entitlement2.getExternalKey(), account.getExternalKey());

            assertEquals(entitlement2.getEffectiveStartDate(), initialDate);
            assertNull(entitlement2.getEffectiveEndDate());

            assertEquals(entitlement2.getPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(entitlement2.getProduct().getName(), "Shotgun");
            assertEquals(entitlement2.getCurrentPhase().getName(), "shotgun-monthly-trial");
            assertEquals(entitlement2.getPlan().getName(), "shotgun-monthly");
            assertEquals(entitlement2.getProductCategory(), ProductCategory.BASE);

            assertEquals(entitlement2.getState(), EntitlementState.ACTIVE);
            assertEquals(entitlement2.getSourceType(), EntitlementSourceType.NATIVE);

            assertEquals(entitlement2.getLastActivePlan().getName(), "shotgun-monthly");
            assertEquals(entitlement2.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(entitlement2.getLastActiveProduct().getName(), "Shotgun");
            assertEquals(entitlement2.getLastActiveProductCategory(), ProductCategory.BASE);

            assertEquals(entitlement2.getState(), EntitlementState.ACTIVE);
            assertEquals(entitlement2.getSourceType(), EntitlementSourceType.NATIVE);

            // Finally
            List<Entitlement> accountEntitlements = entitlementApi.getAllEntitlementsForAccountId(account.getId(), callContext);
            assertEquals(accountEntitlements.size(), 1);

            final Entitlement entitlement3 = accountEntitlements.get(0);

            assertEquals(entitlement3.getAccountId(), account.getId());
            assertEquals(entitlement3.getExternalKey(), account.getExternalKey());

            assertEquals(entitlement3.getEffectiveStartDate(), initialDate);
            assertNull(entitlement3.getEffectiveEndDate());

            assertEquals(entitlement3.getPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(entitlement3.getProduct().getName(), "Shotgun");
            assertEquals(entitlement3.getCurrentPhase().getName(), "shotgun-monthly-trial");
            assertEquals(entitlement3.getPlan().getName(), "shotgun-monthly");
            assertEquals(entitlement3.getProductCategory(), ProductCategory.BASE);

            assertEquals(entitlement3.getState(), EntitlementState.ACTIVE);
            assertEquals(entitlement3.getSourceType(), EntitlementSourceType.NATIVE);

            assertEquals(entitlement3.getLastActivePlan().getName(), "shotgun-monthly");
            assertEquals(entitlement3.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(entitlement3.getLastActiveProduct().getName(), "Shotgun");
            assertEquals(entitlement3.getLastActiveProductCategory(), ProductCategory.BASE);

            assertEquals(entitlement3.getState(), EntitlementState.ACTIVE);
            assertEquals(entitlement3.getSourceType(), EntitlementSourceType.NATIVE);


        } catch (EntitlementApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        } catch (AccountApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testAddEntitlement() {

        try {

            final LocalDate initialDate = new LocalDate(2013, 8, 7);
            clock.setDay(initialDate);

            final Account account = accountApi.createAccount(getAccountData(7), callContext);

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

            // Create entitlement and check each field
            final Entitlement baseEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);

            // Add ADD_ON
            final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            final Entitlement telescopicEntitlement = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), spec1, initialDate, callContext);

            assertEquals(telescopicEntitlement.getAccountId(), account.getId());
            assertEquals(telescopicEntitlement.getExternalKey(), account.getExternalKey());

            assertEquals(telescopicEntitlement.getEffectiveStartDate(), initialDate);
            assertNull(telescopicEntitlement.getEffectiveEndDate());

            assertEquals(telescopicEntitlement.getPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(telescopicEntitlement.getProduct().getName(), "Telescopic-Scope");
            assertEquals(telescopicEntitlement.getCurrentPhase().getName(), "telescopic-scope-monthly-discount");
            assertEquals(telescopicEntitlement.getPlan().getName(), "telescopic-scope-monthly");
            assertEquals(telescopicEntitlement.getProductCategory(), ProductCategory.ADD_ON);

            List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement.getBundleId(), callContext);
            assertEquals(bundleEntitlements.size(), 2);

            bundleEntitlements = entitlementApi.getAllEntitlementsForAccountIdAndExternalKey(account.getId(), account.getExternalKey(), callContext);
            assertEquals(bundleEntitlements.size(), 2);

        } catch (AccountApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        } catch (EntitlementApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        }
    }


    @Test(groups = "slow")
    public void testPauseUnpause() {

        try {

            final LocalDate initialDate = new LocalDate(2013, 8, 7);
            clock.setDay(initialDate);

            final Account account = accountApi.createAccount(getAccountData(7), callContext);

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

            // Create entitlement and check each field
            final Entitlement baseEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);

            clock.addDays(1);
            final LocalDate effectiveDateSpec1 = new LocalDate(clock.getUTCNow(), account.getTimeZone());
            final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            final Entitlement telescopicEntitlement = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), spec1, effectiveDateSpec1, callContext);

            // Block all entitlement in the bundle
            clock.addDays(5);
            entitlementApi.pause(baseEntitlement.getBundleId(), new LocalDate(clock.getUTCNow()), callContext);

            // Verify blocking state
            final Entitlement baseEntitlement2 = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
            assertEquals(baseEntitlement2.getState(), EntitlementState.BLOCKED);

            final Entitlement telescopicEntitlement2 = entitlementApi.getEntitlementForId(telescopicEntitlement.getId(), callContext);
            assertEquals(telescopicEntitlement2.getState(), EntitlementState.BLOCKED);

            // Check we can't block in a blocked state
            try {
                entitlementApi.pause(baseEntitlement.getBundleId(), new LocalDate(clock.getUTCNow()), callContext);
                Assert.fail("Should not have succeeded to block in a blocked state");
            } catch (EntitlementApiException e) {
                assertEquals(e.getCode(), ErrorCode.ENT_ALREADY_BLOCKED.getCode());
            }


            final List<Entitlement> bundleEntitlements2 = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement2.getBundleId(), callContext);
            assertEquals(bundleEntitlements2.size(), 2);
            for (Entitlement cur : bundleEntitlements2) {
                assertEquals(cur.getState(), EntitlementState.BLOCKED);
            }

            // Try to add an ADD_ON, it should fail
            try {
                final PlanPhaseSpecifier spec3 = new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
                final Entitlement telescopicEntitlement3 = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), spec1, effectiveDateSpec1, callContext);
            } catch (EntitlementApiException e) {
                assertEquals(e.getCode(), ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION.getCode());
            }

            clock.addDays(3);
            entitlementApi.resume(baseEntitlement.getBundleId(), new LocalDate(), callContext);

            // Verify call is idempotent
            entitlementApi.resume(baseEntitlement.getBundleId(), new LocalDate(), callContext);

            // Verify blocking state
            final Entitlement baseEntitlement3 = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
            assertEquals(baseEntitlement3.getState(), EntitlementState.ACTIVE);

            final Entitlement telescopicEntitlement3 = entitlementApi.getEntitlementForId(telescopicEntitlement.getId(), callContext);
            assertEquals(telescopicEntitlement3.getState(), EntitlementState.ACTIVE);

            final List<Entitlement> bundleEntitlements3 = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement2.getBundleId(), callContext);
            assertEquals(bundleEntitlements3.size(), 2);
            for (Entitlement cur : bundleEntitlements3) {
                assertEquals(cur.getState(), EntitlementState.ACTIVE);
            }

            // STEPH_ENT wait for semantics to clarify and write the tests
        } catch (AccountApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        } catch (EntitlementApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        }
    }
}
