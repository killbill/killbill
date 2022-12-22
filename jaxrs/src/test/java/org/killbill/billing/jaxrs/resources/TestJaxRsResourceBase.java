/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.jaxrs.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.customfield.CustomField;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestJaxRsResourceBase extends JaxrsTestSuiteNoDB {

    private CustomFieldUserApi customFieldUserApi;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        if (hasFailed()) {
            return;
        }
        customFieldUserApi = Mockito.mock(CustomFieldUserApi.class);
    }

    private JaxRsResourceBase createJaxRsResourceBase() {
        final JaxRsResourceBase testJaxRsResourceBase = new JaxRsResourceBase(
                null,
                null,
                customFieldUserApi,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ) {};
        return Mockito.spy(testJaxRsResourceBase);
    }

    @Test(groups = "fast")
    public void testExtractPluginProperties() {
        final List<String> pluginPropertiesString = List.of("payment_cryptogram=EHuWW9PiBkWvqE5juRwDzAUFBAk=",
                                                            "cc_number=4111111111111111",
                                                            "cc_type=visa",
                                                            "cc_expiration_month=09",
                                                            "cc_expiration_year=2020");
        final JaxRsResourceBase base = createJaxRsResourceBase();
        final List<PluginProperty> pluginProperties = Iterables.toUnmodifiableList(base.extractPluginProperties(pluginPropertiesString));
        Assert.assertEquals(pluginProperties.size(), 5);
        Assert.assertEquals(pluginProperties.get(0).getKey(), "payment_cryptogram");
        Assert.assertEquals(pluginProperties.get(0).getValue(), "EHuWW9PiBkWvqE5juRwDzAUFBAk=");
        Assert.assertEquals(pluginProperties.get(1).getKey(), "cc_number");
        Assert.assertEquals(pluginProperties.get(1).getValue(), "4111111111111111");
        Assert.assertEquals(pluginProperties.get(2).getKey(), "cc_type");
        Assert.assertEquals(pluginProperties.get(2).getValue(), "visa");
        Assert.assertEquals(pluginProperties.get(3).getKey(), "cc_expiration_month");
        Assert.assertEquals(pluginProperties.get(3).getValue(), "09");
        Assert.assertEquals(pluginProperties.get(4).getKey(), "cc_expiration_year");
        Assert.assertEquals(pluginProperties.get(4).getValue(), "2020");
    }

    @Test(groups = "fast")
    public void testExtractPluginPropertiesWithNullProperty() {
        final JaxRsResourceBase base = createJaxRsResourceBase();
        final List<String> pluginPropertiesString = List.of("foo=", "bar=ttt");
        final List<PluginProperty> pluginProperties = Iterables.toUnmodifiableList(base.extractPluginProperties(pluginPropertiesString));
        Assert.assertEquals(pluginProperties.size(), 1);
        Assert.assertEquals(pluginProperties.get(0).getKey(), "bar");
        Assert.assertEquals(pluginProperties.get(0).getValue(), "ttt");
    }

    private List<CustomField> createCustomFields() {
        final List<CustomField> result = new ArrayList<>();

        int i = 1;
        while (i <= 5) {
            final CustomField customField = Mockito.mock(CustomField.class);
            Mockito.when(customField.getId()).thenReturn(UUIDs.randomUUID());
            Mockito.when(customField.getFieldName()).thenReturn(String.valueOf(i));
            result.add(customField);
            i++;
        }

        return result;
    }

    @Test(groups = "fast")
    public void testDeleteCustomFieldsWithSomeIds() throws CustomFieldApiException {
        final UUID id = UUIDs.randomUUID();
        final List<CustomField> fromDatabase = createCustomFields();
        final List<UUID> idsToCheck = fromDatabase.stream()
                .filter(customField -> customField.getFieldName().contains("1") || customField.getFieldName().contains("2"))
                .map(CustomField::getId)
                .collect(Collectors.toUnmodifiableList());

        final JaxRsResourceBase base = createJaxRsResourceBase();
        Mockito.when(customFieldUserApi.getCustomFieldsForObject(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(fromDatabase);

        base.deleteCustomFields(id, idsToCheck, callContext);

        Mockito.verify(customFieldUserApi, Mockito.times(1)).removeCustomFields(Mockito.anyList(), Mockito.any());
    }

    @Test(groups = "fast")
    public void testDeleteCustomFieldsWithoutMatchingIds() throws CustomFieldApiException {
        final UUID id = UUIDs.randomUUID();
        final List<CustomField> fromDatabase = createCustomFields();
        final List<UUID> idsToCheck = List.of(UUIDs.randomUUID(), UUIDs.randomUUID(), UUIDs.randomUUID());

        final JaxRsResourceBase base = createJaxRsResourceBase();
        Mockito.when(customFieldUserApi.getCustomFieldsForObject(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(fromDatabase);

        base.deleteCustomFields(id, idsToCheck, callContext);

        Mockito.verify(customFieldUserApi, Mockito.never()).removeCustomFields(Mockito.anyList(), Mockito.any());
    }

    @Test(groups = "fast")
    public void testDeleteCustomFieldsWithEmptyIds() throws CustomFieldApiException {
        final UUID id = UUIDs.randomUUID();
        final List<CustomField> fromDatabase = createCustomFields();
        final List<UUID> idsToCheck = Collections.emptyList();

        final JaxRsResourceBase base = createJaxRsResourceBase();
        Mockito.when(customFieldUserApi.getCustomFieldsForObject(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(fromDatabase);

        base.deleteCustomFields(id, idsToCheck, callContext);

        Mockito.verify(customFieldUserApi, Mockito.times(1)).removeCustomFields(Mockito.anyList(), Mockito.any());
    }

    @Test(groups = "fast")
    public void testDeleteCustomFieldsWithNullIds() throws CustomFieldApiException {
        final UUID id = UUIDs.randomUUID();
        final List<CustomField> fromDatabase = createCustomFields();

        final JaxRsResourceBase base = createJaxRsResourceBase();
        Mockito.when(customFieldUserApi.getCustomFieldsForObject(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(fromDatabase);

        base.deleteCustomFields(id, null, callContext);

        Mockito.verify(customFieldUserApi, Mockito.times(1)).removeCustomFields(Mockito.anyList(), Mockito.any());
    }
}
