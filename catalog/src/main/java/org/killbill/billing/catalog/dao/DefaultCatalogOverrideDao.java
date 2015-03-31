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

package org.killbill.billing.catalog.dao;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.clock.Clock;
import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import com.google.inject.Inject;

public class DefaultCatalogOverrideDao implements CatalogOverrideDao {

    private final IDBI dbi;
    private final Clock clock;

    @Inject
    public DefaultCatalogOverrideDao(final IDBI dbi, final Clock clock) {
        this.dbi = dbi;
        this.clock = clock;
        // There is no real good place to do that but here (since the sqlDao are NOT EntitySqlDao and DBPProvider belongs in common)... oh well..
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverridePlanDefinitionModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverridePhaseDefinitionModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverridePlanPhaseModelDao.class));
    }

    @Override
    public CatalogOverridePlanDefinitionModelDao getOrCreateOverridePlanDefinition(final String parentPlanName, final DateTime catalogEffectiveDate, final PlanPhasePriceOverride[] resolvedOverride, final InternalCallContext context) {

        return dbi.inTransaction(new TransactionCallback<CatalogOverridePlanDefinitionModelDao>() {
            @Override
            public CatalogOverridePlanDefinitionModelDao inTransaction(final Handle handle, final TransactionStatus status) throws Exception {

                final CatalogOverridePhaseDefinitionModelDao[] overridePhaseDefinitionModelDaos = new CatalogOverridePhaseDefinitionModelDao[resolvedOverride.length];
                for (int i = 0; i < resolvedOverride.length; i++) {
                    final PlanPhasePriceOverride curOverride = resolvedOverride[i];
                    if (curOverride != null) {
                        final CatalogOverridePhaseDefinitionModelDao createdOverridePhaseDefinitionModelDao = getOrCreateOverridePhaseDefinitionFromTransaction(curOverride.getPhaseName(), catalogEffectiveDate, curOverride, handle, context);
                        overridePhaseDefinitionModelDaos[i] = createdOverridePhaseDefinitionModelDao;
                    }
                }

                final CatalogOverridePlanDefinitionSqlDao sqlDao = handle.attach(CatalogOverridePlanDefinitionSqlDao.class);
                final Long targetPlanDefinitionRecordId = getOverridePlanDefinitionFromTransaction(overridePhaseDefinitionModelDaos, handle, context);
                if (targetPlanDefinitionRecordId != null) {
                    return sqlDao.getByRecordId(targetPlanDefinitionRecordId, context);
                }

                final CatalogOverridePlanDefinitionModelDao inputPlanDef = new CatalogOverridePlanDefinitionModelDao(parentPlanName, true, catalogEffectiveDate);
                sqlDao.create(inputPlanDef, context);
                final Long recordId = sqlDao.getLastInsertId();
                final CatalogOverridePlanDefinitionModelDao resultPlanDef = sqlDao.getByRecordId(recordId, context);

                for (short i = 0; i < overridePhaseDefinitionModelDaos.length; i++) {
                    if (overridePhaseDefinitionModelDaos[i] != null) {
                        createCatalogOverridePlanPhaseFromTransaction(i, overridePhaseDefinitionModelDaos[i], resultPlanDef, handle, context);
                    }
                }
                return resultPlanDef;
            }
        });
    }

    @Override
    public List<CatalogOverridePhaseDefinitionModelDao> getOverriddenPlanPhases(final Long planDefRecordId, final InternalTenantContext context) {
        return dbi.inTransaction(new TransactionCallback<List<CatalogOverridePhaseDefinitionModelDao>>() {
            @Override
            public List<CatalogOverridePhaseDefinitionModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverridePhaseDefinitionSqlDao sqlDao = handle.attach(CatalogOverridePhaseDefinitionSqlDao.class);
                return sqlDao.getOverriddenPlanPhases(planDefRecordId, context);
            }
        });
    }

    private Long getOverridePlanDefinitionFromTransaction(final CatalogOverridePhaseDefinitionModelDao[] overridePhaseDefinitionModelDaos, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePlanPhaseSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePlanPhaseSqlDao.class);

        final List<String> keys = new ArrayList<String>();
        for (int i = 0; i < overridePhaseDefinitionModelDaos.length; i++) {
            final CatalogOverridePhaseDefinitionModelDao cur = overridePhaseDefinitionModelDaos[i];
            if (cur != null) {
                // Each key is the concatenation of the phase_number, phase_definition_record_id
                final StringBuffer key = new StringBuffer();
                key.append(i);
                key.append(",");
                key.append(cur.getRecordId());
                keys.add(key.toString());
            }
        }
        return keys.size() > 0 ? sqlDao.getTargetPlanDefinition(keys, keys.size(), context) : null;
    }

    private void createCatalogOverridePlanPhaseFromTransaction(final short phaseNum, final CatalogOverridePhaseDefinitionModelDao phaseDef, final CatalogOverridePlanDefinitionModelDao planDef, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePlanPhaseSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePlanPhaseSqlDao.class);
        final CatalogOverridePlanPhaseModelDao modelDao = new CatalogOverridePlanPhaseModelDao(phaseNum, phaseDef.getRecordId(), planDef.getRecordId());
        sqlDao.create(modelDao, context);
    }

    private CatalogOverridePhaseDefinitionModelDao getOrCreateOverridePhaseDefinitionFromTransaction(final String parentPhaseName, final DateTime catalogEffectiveDate, final PlanPhasePriceOverride override, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePhaseDefinitionSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePhaseDefinitionSqlDao.class);
        CatalogOverridePhaseDefinitionModelDao result = sqlDao.getByAttributes(parentPhaseName, override.getCurrency().name(), override.getFixedPrice(), override.getRecurringPrice(), context);
        if (result == null) {
            final CatalogOverridePhaseDefinitionModelDao phaseDef = new CatalogOverridePhaseDefinitionModelDao(parentPhaseName, override.getCurrency().name(), override.getFixedPrice(), override.getRecurringPrice(),
                                                                                                               catalogEffectiveDate);
            sqlDao.create(phaseDef, context);
            final Long recordId = sqlDao.getLastInsertId();
            result = sqlDao.getByRecordId(recordId, context);
        }
        return result;
    }
}
