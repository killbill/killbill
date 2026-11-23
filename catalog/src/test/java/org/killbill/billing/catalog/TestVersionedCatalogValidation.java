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

package org.killbill.billing.catalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.xmlloader.ValidationErrors;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestVersionedCatalogValidation extends CatalogTestSuiteNoDB {

    // Mimics DefaultCatalogUserApi.validateCatalogInternal, which Java-serializes
    // (clones) the existing catalog before validating the new version against it.
    // The clone drops each plan's catalog back-reference (DefaultPlan.readExternal
    // does not restore it), so plan.getCatalog() is null on the cloned plans.
    private DefaultVersionedCatalog clone(final DefaultVersionedCatalog versionedCatalog) throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final ObjectOutput out = new ObjectOutputStream(bos);
        out.writeObject(versionedCatalog);
        final ObjectInput in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        return (DefaultVersionedCatalog) in.readObject();
    }

    @Test(groups = "fast")
    public void testValidatePhaseMismatchDoesNotThrowAfterClone() throws Exception {
        final DefaultVersionedCatalog versionedCatalog = new DefaultVersionedCatalog();
        versionedCatalog.add(getCatalog("versionedCatalog/PhaseMismatchV1.xml"));
        versionedCatalog.add(getCatalog("versionedCatalog/PhaseMismatchV2.xml"));

        final DefaultVersionedCatalog cloned = clone(versionedCatalog);
        final ValidationErrors errors = new ValidationErrors();
        cloned.validate(null, errors);

        Assert.assertEquals(errors.size(), 1);
        final String description = errors.get(0).getDescription();
        Assert.assertTrue(description.contains("Phase 'standard-monthly-evergreen'for plan 'standard-monthly'"));
        Assert.assertTrue(description.contains("does not exist in version"));
        // The version dates must still be reported, not null (see #1945)
        Assert.assertTrue(description.contains("2020"));
    }

    @Test(groups = "fast")
    public void testValidateDurationViolationDoesNotThrowAfterClone() throws Exception {
        final StandaloneCatalog catalog = getCatalog("versionedCatalog/PhaseMismatchV1.xml");
        // Give the EVERGREEN final phase a finite duration: invalid, but only
        // detectable by validation (load-time validation never saw this catalog).
        for (final Plan plan : catalog.getPlans()) {
            if ("standard-monthly".equals(plan.getName())) {
                ((DefaultPlanPhase) ((DefaultPlan) plan).getFinalPhase())
                        .setDuration(new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(30));
            }
        }

        final DefaultVersionedCatalog versionedCatalog = new DefaultVersionedCatalog();
        versionedCatalog.add(catalog);

        final DefaultVersionedCatalog cloned = clone(versionedCatalog);
        final ValidationErrors errors = new ValidationErrors();
        cloned.validate(null, errors);

        Assert.assertEquals(errors.size(), 1);
        Assert.assertTrue(errors.get(0).getDescription().contains("must have duration as UNLIMITED"));
    }
}
