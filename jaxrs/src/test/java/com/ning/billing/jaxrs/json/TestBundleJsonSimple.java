package com.ning.billing.jaxrs.json;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestBundleJsonSimple {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String bundleId = UUID.randomUUID().toString();
        final String externalKey = UUID.randomUUID().toString();
        final BundleJsonSimple bundleJsonSimple = new BundleJsonSimple(bundleId, externalKey);
        Assert.assertEquals(bundleJsonSimple.getBundleId(), bundleId);
        Assert.assertEquals(bundleJsonSimple.getExternalKey(), externalKey);

        final String asJson = mapper.writeValueAsString(bundleJsonSimple);
        Assert.assertEquals(asJson, "{\"bundleId\":\"" + bundleJsonSimple.getBundleId() + "\"," +
                "\"externalKey\":\"" + bundleJsonSimple.getExternalKey() + "\"}");

        final BundleJsonSimple fromJson = mapper.readValue(asJson, BundleJsonSimple.class);
        Assert.assertEquals(fromJson, bundleJsonSimple);
    }
}
