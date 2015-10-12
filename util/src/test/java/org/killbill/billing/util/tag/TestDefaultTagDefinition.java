/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.util.tag;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDefaultTagDefinition extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testDefaultTagDefinition() throws TagApiException, TagDefinitionApiException {

        final DefaultTagDefinition def1 = new DefaultTagDefinition(UUID.randomUUID(), "foo", "nothing", false);
        Assert.assertFalse(def1.getApplicableObjectTypes().isEmpty());
        Assert.assertEquals(ImmutableList.<ObjectType>copyOf(ObjectType.values()), def1.getApplicableObjectTypes());

        for (final ControlTagType cur : ControlTagType.values()) {

            final DefaultTagDefinition curDef = new DefaultTagDefinition(cur.getId(), cur.name(), cur.getDescription(), true);
            Assert.assertFalse(curDef.getApplicableObjectTypes().isEmpty());
            Assert.assertEquals(curDef.getApplicableObjectTypes(), cur.getApplicableObjectTypes());
        }

        try {
            new DefaultTagDefinition(UUID.randomUUID(), "bar", "nothing again", true);
            Assert.fail("Not a control tag type");
        } catch (final IllegalStateException e) {
        }
    }
}
