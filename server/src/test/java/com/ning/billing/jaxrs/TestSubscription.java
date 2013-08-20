package com.ning.billing.jaxrs;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.EntitlementJsonNoEvents;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import static org.testng.Assert.assertEquals;

public class TestSubscription extends TestJaxrsBase {



    @Test(groups = "slow")
    public void testEntitlementInTrialOk() throws Exception {

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("xil", "shdxilhkkl", "xil@yahoo.com");

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final EntitlementJsonNoEvents entitlementJson = createEntitlement(accountJson.getAccountId(), "99999", productName, ProductCategory.BASE.toString(), term.toString(), true);

        String uri = JaxrsResource.SUBSCRIPTIONS_PATH + "/" + entitlementJson.getEntitlementId();

        // Retrieves with GET
        Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        SubscriptionJsonNoEvents objFromJson = mapper.readValue(baseJson, SubscriptionJsonNoEvents.class);

        Assert.assertNotNull(objFromJson.getChargedThroughDate());
        Assert.assertEquals(objFromJson.getChargedThroughDate(), new LocalDate("2012-04-25"));
    }
}
