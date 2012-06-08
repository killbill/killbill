package com.ning.billing.jaxrs.json;

import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

public class TestBundleJsonNoSubscriptions {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String bundleId = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();
        final String externalKey = UUID.randomUUID().toString();
        final BundleJsonNoSubscriptions bundleJsonNoSubscriptions = new BundleJsonNoSubscriptions(bundleId, accountId, externalKey, null);
        Assert.assertEquals(bundleJsonNoSubscriptions.getBundleId(), bundleId);
        Assert.assertEquals(bundleJsonNoSubscriptions.getAccountId(), accountId);
        Assert.assertEquals(bundleJsonNoSubscriptions.getExternalKey(), externalKey);

        final String asJson = mapper.writeValueAsString(bundleJsonNoSubscriptions);
        Assert.assertEquals(asJson, "{\"bundleId\":\"" + bundleJsonNoSubscriptions.getBundleId() + "\"," +
                "\"accountId\":\"" + bundleJsonNoSubscriptions.getAccountId() + "\"," +
                "\"externalKey\":\"" + bundleJsonNoSubscriptions.getExternalKey() + "\"}");

        final BundleJsonNoSubscriptions fromJson = mapper.readValue(asJson, BundleJsonNoSubscriptions.class);
        Assert.assertEquals(fromJson, bundleJsonNoSubscriptions);
    }

    @Test(groups = "fast")
    public void testFromSubscriptionBundle() throws Exception {
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final UUID accountId = UUID.randomUUID();
        Mockito.when(bundle.getId()).thenReturn(bundleId);
        Mockito.when(bundle.getKey()).thenReturn(externalKey);
        Mockito.when(bundle.getAccountId()).thenReturn(accountId);

        final BundleJsonNoSubscriptions bundleJsonNoSubscriptions = new BundleJsonNoSubscriptions(bundle);
        Assert.assertEquals(bundleJsonNoSubscriptions.getBundleId(), bundleId.toString());
        Assert.assertEquals(bundleJsonNoSubscriptions.getExternalKey(), externalKey);
        Assert.assertEquals(bundleJsonNoSubscriptions.getAccountId(), accountId.toString());
    }
}
