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
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsagePriceOverride;
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
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverrideBlockDefinitionModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverrideTierBlockModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverrideTierDefinitionModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverrideUsageDefinitionModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverrideUsageTierModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverridePhaseUsageModelDao.class));
    }

    @Override
    public CatalogOverridePlanDefinitionModelDao getOrCreateOverridePlanDefinition(final Plan parentPlan, final DateTime catalogEffectiveDate, final PlanPhasePriceOverride[] resolvedOverride, final InternalCallContext context) {

        return dbi.inTransaction(new TransactionCallback<CatalogOverridePlanDefinitionModelDao>() {
            @Override
            public CatalogOverridePlanDefinitionModelDao inTransaction(final Handle handle, final TransactionStatus status) throws Exception {

                final CatalogOverridePhaseDefinitionModelDao[] overridePhaseDefinitionModelDaos = new CatalogOverridePhaseDefinitionModelDao[resolvedOverride.length];
                for (int i = 0; i < resolvedOverride.length; i++) {
                    final PlanPhasePriceOverride curOverride = resolvedOverride[i];
                    if (curOverride != null) {
                        PlanPhase parentPlanPhase = parentPlan.getAllPhases()[i];
                        final CatalogOverridePhaseDefinitionModelDao createdOverridePhaseDefinitionModelDao = getOrCreateOverridePhaseDefinitionFromTransaction(parentPlanPhase, curOverride.getPhaseName(),curOverride.getCurrency(), catalogEffectiveDate, curOverride, handle, context);
                        overridePhaseDefinitionModelDaos[i] = createdOverridePhaseDefinitionModelDao;
                    }
                }

                final CatalogOverridePlanDefinitionSqlDao sqlDao = handle.attach(CatalogOverridePlanDefinitionSqlDao.class);
                final Long targetPlanDefinitionRecordId = getOverridePlanDefinitionFromTransaction(overridePhaseDefinitionModelDaos, handle, context);
                if (targetPlanDefinitionRecordId != null) {
                    return sqlDao.getByRecordId(targetPlanDefinitionRecordId, context);
                }

                final CatalogOverridePlanDefinitionModelDao inputPlanDef = new CatalogOverridePlanDefinitionModelDao(parentPlan.getName(), true, catalogEffectiveDate);
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

    private Long getOverridePlanDefinitionFromTransaction(final CatalogOverridePhaseDefinitionModelDao[] overridePhaseDefinitionModelDaos, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePlanPhaseSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePlanPhaseSqlDao.class);

        final List<String> keys = new ArrayList<String>();
        for (int i = 0; i < overridePhaseDefinitionModelDaos.length; i++) {
            final CatalogOverridePhaseDefinitionModelDao cur = overridePhaseDefinitionModelDaos[i];
            if (cur != null) {
                // Each key is the concatenation of the phase_number, phase_definition_record_id
                keys.add(getConcatenatedKey(i, cur.getRecordId()).toString());
            }
        }
        return keys.size() > 0 ? sqlDao.getTargetPlanDefinition(keys, keys.size(), context) : null;
    }

    private void createCatalogOverridePlanPhaseFromTransaction(final short phaseNum, final CatalogOverridePhaseDefinitionModelDao phaseDef, final CatalogOverridePlanDefinitionModelDao planDef, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePlanPhaseSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePlanPhaseSqlDao.class);
        final CatalogOverridePlanPhaseModelDao modelDao = new CatalogOverridePlanPhaseModelDao(phaseNum, phaseDef.getRecordId(), planDef.getRecordId());
        sqlDao.create(modelDao, context);
    }

    private CatalogOverridePhaseDefinitionModelDao getOrCreateOverridePhaseDefinitionFromTransaction(final PlanPhase parentPlanPhase, final String parentPhaseName, final Currency currency, final DateTime catalogEffectiveDate, final PlanPhasePriceOverride override, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePhaseDefinitionSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePhaseDefinitionSqlDao.class);

        if(override.getUsagePriceOverrides() == null || (override.getUsagePriceOverrides() != null && isUsageOverrideListHasOnlyNull(override.getUsagePriceOverrides()))) {
            return getOrCreatePhaseDefinitionFromTransactionWithoutUsageOverrides(parentPhaseName, catalogEffectiveDate, override, inTransactionHandle, context);
        }

        final CatalogOverrideUsageDefinitionModelDao[] overrideUsageDefinitionModelDaos = new CatalogOverrideUsageDefinitionModelDao[override.getUsagePriceOverrides().size()];
         List<UsagePriceOverride> resolvedUsageOverrides = override.getUsagePriceOverrides();
          for (int i = 0; i < resolvedUsageOverrides.size(); i++) {
            final UsagePriceOverride curOverride = resolvedUsageOverrides.get(i);
             if (curOverride != null) {
                Usage parentUsage = parentPlanPhase.getUsages()[i];
                final CatalogOverrideUsageDefinitionModelDao createdOverrideUsageDefinitionModelDao = getOrCreateOverrideUsageDefinitionFromTransaction(parentUsage, currency, catalogEffectiveDate, curOverride, inTransactionHandle, context);
                overrideUsageDefinitionModelDaos[i] = createdOverrideUsageDefinitionModelDao;
             }
          }

        final List<Long> targetPhaseDefinitionRecordIds = getOverridePhaseDefinitionFromTransaction(overrideUsageDefinitionModelDaos, inTransactionHandle, context);
        List<CatalogOverridePhaseDefinitionModelDao> results = sqlDao.getByAttributes(parentPhaseName, override.getCurrency().name(), override.getFixedPrice(), override.getRecurringPrice(), context);

        for(CatalogOverridePhaseDefinitionModelDao phase : results) {
            if (targetPhaseDefinitionRecordIds != null && targetPhaseDefinitionRecordIds.contains(phase.getRecordId())) {
                return phase;
            }
        }

        final CatalogOverridePhaseDefinitionModelDao inputPhaseDef = new CatalogOverridePhaseDefinitionModelDao(parentPhaseName, override.getCurrency().name(), override.getFixedPrice(), override.getRecurringPrice(),
                catalogEffectiveDate);
        sqlDao.create(inputPhaseDef, context);
        final Long recordId = sqlDao.getLastInsertId();
        final CatalogOverridePhaseDefinitionModelDao resultPhaseDef = sqlDao.getByRecordId(recordId, context);

        for (short i = 0; i < overrideUsageDefinitionModelDaos.length; i++) {
            if (overrideUsageDefinitionModelDaos[i] != null) {
                createCatalogOverridePhaseUsageFromTransaction(i, overrideUsageDefinitionModelDaos[i], resultPhaseDef, inTransactionHandle, context);
            }
        }
        return resultPhaseDef;
    }

    private CatalogOverridePhaseDefinitionModelDao getOrCreatePhaseDefinitionFromTransactionWithoutUsageOverrides(String parentPhaseName,final DateTime catalogEffectiveDate, final PlanPhasePriceOverride override, final Handle inTransactionHandle, final InternalCallContext context) {

        final CatalogOverridePhaseDefinitionSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePhaseDefinitionSqlDao.class);
        List<CatalogOverridePhaseDefinitionModelDao> resultPhases = sqlDao.getByAttributes(parentPhaseName, override.getCurrency().name(), override.getFixedPrice(), override.getRecurringPrice(), context);
        for(CatalogOverridePhaseDefinitionModelDao resultPhase : resultPhases) {
            if (resultPhase != null && getOverriddenPhaseUsages(resultPhase.getRecordId(), context).size() == 0) {
                return resultPhase;
            }
        }

        final CatalogOverridePhaseDefinitionModelDao phaseDef = new CatalogOverridePhaseDefinitionModelDao(parentPhaseName, override.getCurrency().name(), override.getFixedPrice(), override.getRecurringPrice(),
                catalogEffectiveDate);
        sqlDao.create(phaseDef, context);
        final Long recordId = sqlDao.getLastInsertId();
        return sqlDao.getByRecordId(recordId, context);
    }

    private List<Long> getOverridePhaseDefinitionFromTransaction(final CatalogOverrideUsageDefinitionModelDao[] overrideUsageDefinitionModelDaos, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePhaseUsageSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePhaseUsageSqlDao.class);

        final List<String> keys = new ArrayList<String>();
        for (int i = 0; i < overrideUsageDefinitionModelDaos.length; i++) {
            final CatalogOverrideUsageDefinitionModelDao cur = overrideUsageDefinitionModelDaos[i];
            // Each key is the concatenation of the usage_number, usage_definition_record_id
            if (cur != null) {
                keys.add(getConcatenatedKey(i, cur.getRecordId()).toString());
            }
        }
        return keys.size() > 0 ? sqlDao.getTargetPhaseDefinition(keys, keys.size(), context) : null;
    }

    private void createCatalogOverridePhaseUsageFromTransaction(final short usageNum, final CatalogOverrideUsageDefinitionModelDao usageDef, final CatalogOverridePhaseDefinitionModelDao phaseDef, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePhaseUsageSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePhaseUsageSqlDao.class);
        final CatalogOverridePhaseUsageModelDao modelDao = new CatalogOverridePhaseUsageModelDao(usageNum, usageDef.getRecordId(), phaseDef.getRecordId());
        sqlDao.create(modelDao, context);
    }

    private CatalogOverrideUsageDefinitionModelDao getOrCreateOverrideUsageDefinitionFromTransaction(final Usage parentUsage, Currency currency, final DateTime catalogEffectiveDate, final UsagePriceOverride override, final Handle inTransactionHandle, final InternalCallContext context){

        final List<TierPriceOverride> resolvedTierOverrides = override.getTierPriceOverrides();

        final CatalogOverrideTierDefinitionModelDao[] overrideTierDefinitionModelDaos = new CatalogOverrideTierDefinitionModelDao[resolvedTierOverrides.size()];
        for (int i = 0; i < resolvedTierOverrides.size(); i++) {
            final TierPriceOverride curOverride = resolvedTierOverrides.get(i);
            if (curOverride != null) {
                Tier parentTier = parentUsage.getTiers()[i];
                final CatalogOverrideTierDefinitionModelDao createdOverrideTierDefinitionModelDao = getOrCreateOverrideTierDefinitionFromTransaction(parentTier, curOverride, currency, catalogEffectiveDate, inTransactionHandle, context);
                overrideTierDefinitionModelDaos[i] = createdOverrideTierDefinitionModelDao;
            }
        }

        final CatalogOverrideUsageDefinitionSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideUsageDefinitionSqlDao.class);
        final List<Long> targetUsageDefinitionRecordIds = getOverrideUsageDefinitionFromTransaction(overrideTierDefinitionModelDaos, inTransactionHandle, context);

        List<CatalogOverrideUsageDefinitionModelDao> results = sqlDao.getByAttributes(parentUsage.getName(), context);

        for(CatalogOverrideUsageDefinitionModelDao usage : results) {
            if (targetUsageDefinitionRecordIds != null && targetUsageDefinitionRecordIds.contains(usage.getRecordId())) {
                return usage;
            }
        }

        final CatalogOverrideUsageDefinitionModelDao inputUsageDef = new CatalogOverrideUsageDefinitionModelDao(parentUsage.getName(), parentUsage.getUsageType().name(), currency.name(), null, null, catalogEffectiveDate);
        sqlDao.create(inputUsageDef, context);
        final Long recordId = sqlDao.getLastInsertId();
        final CatalogOverrideUsageDefinitionModelDao resultUsageDef = sqlDao.getByRecordId(recordId, context);

        for (short i = 0; i < overrideTierDefinitionModelDaos.length; i++) {
            if (overrideTierDefinitionModelDaos[i] != null) {
                createCatalogOverrideUsageTierFromTransaction(i, overrideTierDefinitionModelDaos[i], resultUsageDef, inTransactionHandle, context);
            }
        }
        return resultUsageDef;
    }

    private List<Long> getOverrideUsageDefinitionFromTransaction(final CatalogOverrideTierDefinitionModelDao[] overrideTierDefinitionModelDaos, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverrideUsageTierSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideUsageTierSqlDao.class);

        final List<String> keys = new ArrayList<String>();
        for (int i = 0; i < overrideTierDefinitionModelDaos.length; i++) {
            final CatalogOverrideTierDefinitionModelDao cur = overrideTierDefinitionModelDaos[i];
            if (cur != null) {
                // Each key is the concatenation of the tier_number, tier_definition_record_id
                keys.add(getConcatenatedKey(i, cur.getRecordId()).toString());
            }
        }
        return keys.size() > 0 ? sqlDao.getTargetUsageDefinition(keys, keys.size(), context) : null;
    }

    private void createCatalogOverrideUsageTierFromTransaction(final short tierNum, final CatalogOverrideTierDefinitionModelDao tierDef, final CatalogOverrideUsageDefinitionModelDao usageDef, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverrideUsageTierSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideUsageTierSqlDao.class);
        final CatalogOverrideUsageTierModelDao modelDao = new CatalogOverrideUsageTierModelDao(tierNum, tierDef.getRecordId(), usageDef.getRecordId());
        sqlDao.create(modelDao, context);
    }

    private CatalogOverrideTierDefinitionModelDao getOrCreateOverrideTierDefinitionFromTransaction(final Tier parentTier, final TierPriceOverride tierPriceOverride,Currency currency, final DateTime catalogEffectiveDate,  final Handle inTransactionHandle, final InternalCallContext context){

        final List<TieredBlockPriceOverride> resolvedTierBlockOverrides =  tierPriceOverride.getTieredBlockPriceOverrides();

        final CatalogOverrideBlockDefinitionModelDao[] overrideBlockDefinitionModelDaos = new CatalogOverrideBlockDefinitionModelDao[resolvedTierBlockOverrides.size()];
        for (int i = 0; i < resolvedTierBlockOverrides.size(); i++) {
            final TieredBlockPriceOverride curOverride = resolvedTierBlockOverrides.get(i);
            if (curOverride != null) {
                final CatalogOverrideBlockDefinitionModelDao createdOverrideBlockDefinitionModelDao = getOrCreateOverriddenBlockDefinitionFromTransaction(curOverride,catalogEffectiveDate, currency.name(), inTransactionHandle, context);
                overrideBlockDefinitionModelDaos[i] = createdOverrideBlockDefinitionModelDao;
            }
        }

        final CatalogOverrideTierDefinitionSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideTierDefinitionSqlDao.class);
        final Long targetTierDefinitionRecordId = getOverrideTierDefinitionFromTransaction(overrideBlockDefinitionModelDaos, inTransactionHandle, context);
        if (targetTierDefinitionRecordId != null) {
            return sqlDao.getByRecordId(targetTierDefinitionRecordId, context);
        }

        final CatalogOverrideTierDefinitionModelDao inputTierDef = new CatalogOverrideTierDefinitionModelDao(currency.name(), null, null, catalogEffectiveDate);
        sqlDao.create(inputTierDef, context);
        final Long recordId = sqlDao.getLastInsertId();
        final CatalogOverrideTierDefinitionModelDao resultTierDef = sqlDao.getByRecordId(recordId, context);

        for (short i = 0; i < overrideBlockDefinitionModelDaos.length; i++) {
            if (overrideBlockDefinitionModelDaos[i] != null) {
                createCatalogOverrideTierBlockFromTransaction(i, overrideBlockDefinitionModelDaos[i], resultTierDef, inTransactionHandle, context);
            }
        }
        return resultTierDef;
    }

    private void createCatalogOverrideTierBlockFromTransaction(final short blockNum, final CatalogOverrideBlockDefinitionModelDao blockDef, final CatalogOverrideTierDefinitionModelDao tierDef, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverrideTierBlockSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideTierBlockSqlDao.class);
        final CatalogOverrideTierBlockModelDao modelDao = new CatalogOverrideTierBlockModelDao(blockNum, blockDef.getRecordId(), tierDef.getRecordId());
        sqlDao.create(modelDao, context);
    }

    private Long getOverrideTierDefinitionFromTransaction(final CatalogOverrideBlockDefinitionModelDao[] overrideBlockDefinitionModelDaos, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverrideTierBlockSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideTierBlockSqlDao.class);

        final List<String> keys = new ArrayList<String>();
        for (int i = 0; i < overrideBlockDefinitionModelDaos.length; i++) {
            final CatalogOverrideBlockDefinitionModelDao cur = overrideBlockDefinitionModelDaos[i];
            if (cur != null) {
                // Each key is the concatenation of the block_number, block_definition_record_id
                keys.add(getConcatenatedKey(i, cur.getRecordId()).toString());
            }
        }
        return keys.size() > 0 ? sqlDao.getTargetTierDefinition(keys, keys.size(), context) : null;
    }

    private CatalogOverrideBlockDefinitionModelDao getOrCreateOverriddenBlockDefinitionFromTransaction(TieredBlockPriceOverride tieredBlockPriceOverride,final DateTime catalogEffectiveDate, String currency, final Handle inTransactionHandle, final InternalCallContext context)
    {
        final CatalogOverrideBlockDefinitionSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideBlockDefinitionSqlDao.class);

        CatalogOverrideBlockDefinitionModelDao result = sqlDao.getByAttributes(tieredBlockPriceOverride.getUnitName(),
                currency, tieredBlockPriceOverride.getPrice(), tieredBlockPriceOverride.getMax(),
                tieredBlockPriceOverride.getSize(),context);
        if (result == null) {
            final CatalogOverrideBlockDefinitionModelDao blockDef = new CatalogOverrideBlockDefinitionModelDao(tieredBlockPriceOverride.getUnitName(),currency, tieredBlockPriceOverride.getPrice(),
                    tieredBlockPriceOverride.getSize(),tieredBlockPriceOverride.getMax(), catalogEffectiveDate);
            sqlDao.create(blockDef, context);
            final Long recordId = sqlDao.getLastInsertId();
            result = sqlDao.getByRecordId(recordId, context);
        }
        return result;
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

    @Override
    public List<CatalogOverrideUsageDefinitionModelDao> getOverriddenPhaseUsages(final Long phaseDefRecordId, final InternalTenantContext context) {
        return dbi.inTransaction(new TransactionCallback<List<CatalogOverrideUsageDefinitionModelDao>>() {
            @Override
            public List<CatalogOverrideUsageDefinitionModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverrideUsageDefinitionSqlDao sqlDao = handle.attach(CatalogOverrideUsageDefinitionSqlDao.class);
                return sqlDao.getOverriddenPhaseUsages(phaseDefRecordId, context);
            }
        });
    }

    @Override
    public List<CatalogOverrideTierDefinitionModelDao> getOverriddenUsageTiers(final Long usageDefRecordId, final InternalTenantContext context) {
        return dbi.inTransaction(new TransactionCallback<List<CatalogOverrideTierDefinitionModelDao>>() {
            @Override
            public List<CatalogOverrideTierDefinitionModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverrideTierDefinitionSqlDao sqlDao = handle.attach(CatalogOverrideTierDefinitionSqlDao.class);
                return sqlDao.getOverriddenUsageTiers(usageDefRecordId, context);
            }
        });
    }

    @Override
    public List<CatalogOverrideBlockDefinitionModelDao> getOverriddenTierBlocks(final Long tierDefRecordId, final InternalTenantContext context) {
        return dbi.inTransaction(new TransactionCallback<List<CatalogOverrideBlockDefinitionModelDao>>() {
            @Override
            public List<CatalogOverrideBlockDefinitionModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverrideBlockDefinitionSqlDao sqlDao = handle.attach(CatalogOverrideBlockDefinitionSqlDao.class);
                return sqlDao.getOverriddenTierBlocks(tierDefRecordId, context);
            }
        });
    }


    private boolean isUsageOverrideListHasOnlyNull(List<UsagePriceOverride> usagePriceOverrides) {
        for (UsagePriceOverride override : usagePriceOverrides) {
            if (override != null) {
                return false;
            }
        }
        return true;
    }

    private StringBuffer getConcatenatedKey(int index, Long recordId) {
        final StringBuffer key = new StringBuffer();
        key.append(index);
        key.append(",");
        key.append(recordId);
        return key;
    }
}
