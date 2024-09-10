/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import javax.inject.Inject;

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
import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

public class DefaultCatalogOverrideDao implements CatalogOverrideDao {

    private final IDBI dbi;

    @Inject
    public DefaultCatalogOverrideDao(final IDBI dbi) {
        this.dbi = dbi;
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
                final Long recordId = sqlDao.create(inputPlanDef, context);
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
        if (overridePhaseDefinitionModelDaos.length == 0) {
            return null;
        }
        for (int i = 0; i < overridePhaseDefinitionModelDaos.length; i++) {
            if (overridePhaseDefinitionModelDaos[i] != null) {
                return sqlDao.getTargetPlanDefinition(i, overridePhaseDefinitionModelDaos[i].getRecordId(), context);
            }
        }
        return null;
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

        // If we have some usage overrides, we need to create (or reuse) all the entries
        final CatalogOverrideUsageDefinitionModelDao[] overrideUsageDefinitionModelDaos = new CatalogOverrideUsageDefinitionModelDao[override.getUsagePriceOverrides().size()];
         List<UsagePriceOverride> resolvedUsageOverrides = override.getUsagePriceOverrides();
         // Loop through each usage override section (usually 1)
          for (int i = 0; i < resolvedUsageOverrides.size(); i++) {
            final UsagePriceOverride curOverride = resolvedUsageOverrides.get(i);
             if (curOverride != null) {
                Usage parentUsage = parentPlanPhase.getUsages()[i];
                final CatalogOverrideUsageDefinitionModelDao createdOverrideUsageDefinitionModelDao = getOrCreateOverrideUsageDefinitionFromTransaction(parentUsage, currency, catalogEffectiveDate, curOverride, inTransactionHandle, context);
                overrideUsageDefinitionModelDaos[i] = createdOverrideUsageDefinitionModelDao;
             }
          }

        final Long phaseDefRecordId = getOverridePhaseDefinitionFromTransaction(overrideUsageDefinitionModelDaos, inTransactionHandle, context);
        if (phaseDefRecordId != null) {
            return sqlDao.getByRecordId(phaseDefRecordId, context);
        }

        final CatalogOverridePhaseDefinitionModelDao inputPhaseDef = new CatalogOverridePhaseDefinitionModelDao(parentPhaseName, override.getCurrency().name(), override.getFixedPrice(), override.getRecurringPrice(),
                catalogEffectiveDate);
        final Long recordId = sqlDao.create(inputPhaseDef, context);
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
        final Long recordId = sqlDao.create(phaseDef, context);
        return sqlDao.getByRecordId(recordId, context);
    }

    private Long getOverridePhaseDefinitionFromTransaction(final CatalogOverrideUsageDefinitionModelDao[] overrideUsageDefinitionModelDaos, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePhaseUsageSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePhaseUsageSqlDao.class);
        if (overrideUsageDefinitionModelDaos.length == 0) {
            return null;
        }
        for (int i = 0; i < overrideUsageDefinitionModelDaos.length; i++) {
            if (overrideUsageDefinitionModelDaos[i] != null) {
                return sqlDao.getTargetPhaseDefinition(i, overrideUsageDefinitionModelDaos[i].getRecordId(), context);
            }
        }
        return null;
    }

    private void createCatalogOverridePhaseUsageFromTransaction(final short usageNum, final CatalogOverrideUsageDefinitionModelDao usageDef, final CatalogOverridePhaseDefinitionModelDao phaseDef, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverridePhaseUsageSqlDao sqlDao = inTransactionHandle.attach(CatalogOverridePhaseUsageSqlDao.class);
        final CatalogOverridePhaseUsageModelDao modelDao = new CatalogOverridePhaseUsageModelDao(usageNum, usageDef.getRecordId(), phaseDef.getRecordId());
        sqlDao.create(modelDao, context);
    }

    private CatalogOverrideUsageDefinitionModelDao getOrCreateOverrideUsageDefinitionFromTransaction(final Usage parentUsage, Currency currency, final DateTime catalogEffectiveDate, final UsagePriceOverride override, final Handle inTransactionHandle, final InternalCallContext context){

        final List<TierPriceOverride> resolvedTierOverrides = override.getTierPriceOverrides();

        final CatalogOverrideTierDefinitionModelDao[] overrideTierDefinitionModelDaos = new CatalogOverrideTierDefinitionModelDao[resolvedTierOverrides.size()];
        // Loop through each tier override for the parentUsage (can be several tiers)
        for (int i = 0; i < resolvedTierOverrides.size(); i++) {
            final TierPriceOverride curOverride = resolvedTierOverrides.get(i);
            if (curOverride != null) {
                Tier parentTier = parentUsage.getTiers()[i];
                // Get or create entries in catalog_override_tier_definition and catalog_override_tier_block
                final CatalogOverrideTierDefinitionModelDao createdOverrideTierDefinitionModelDao = getOrCreateOverrideTierDefinitionFromTransaction(parentTier, curOverride, currency, catalogEffectiveDate, inTransactionHandle, context);
                overrideTierDefinitionModelDaos[i] = createdOverrideTierDefinitionModelDao;
            }
        }

        final CatalogOverrideUsageDefinitionSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideUsageDefinitionSqlDao.class);
        final Long usageDefRecordId = getOverrideUsageDefinitionFromTransaction(overrideTierDefinitionModelDaos, inTransactionHandle, context);
        if (usageDefRecordId != null) {
            return sqlDao.getByRecordId(usageDefRecordId, context);
        }

        final CatalogOverrideUsageDefinitionModelDao inputUsageDef = new CatalogOverrideUsageDefinitionModelDao(parentUsage.getName(), parentUsage.getUsageType().name(), currency.name(), null, null, catalogEffectiveDate);
        final Long recordId = sqlDao.create(inputUsageDef, context);
        final CatalogOverrideUsageDefinitionModelDao resultUsageDef = sqlDao.getByRecordId(recordId, context);

        for (short i = 0; i < overrideTierDefinitionModelDaos.length; i++) {
            if (overrideTierDefinitionModelDaos[i] != null) {
                createCatalogOverrideUsageTierFromTransaction(i, overrideTierDefinitionModelDaos[i], resultUsageDef, inTransactionHandle, context);
            }
        }
        return resultUsageDef;
    }

    private Long getOverrideUsageDefinitionFromTransaction(final CatalogOverrideTierDefinitionModelDao[] overrideTierDefinitionModelDaos, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverrideUsageTierSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideUsageTierSqlDao.class);
        if (overrideTierDefinitionModelDaos.length == 0) {
            return null;
        }
        // Use the first non null tier to find the usage definition
        for (int i = 0; i < overrideTierDefinitionModelDaos.length; i++) {
            if (overrideTierDefinitionModelDaos[i] != null) {
                return sqlDao.getTargetUsageDefinition(i, overrideTierDefinitionModelDaos[i].getRecordId(), context);
            }
        }
        return null;
    }

    private void createCatalogOverrideUsageTierFromTransaction(final short tierNum, final CatalogOverrideTierDefinitionModelDao tierDef, final CatalogOverrideUsageDefinitionModelDao usageDef, final Handle inTransactionHandle, final InternalCallContext context) {
        final CatalogOverrideUsageTierSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideUsageTierSqlDao.class);
        final CatalogOverrideUsageTierModelDao modelDao = new CatalogOverrideUsageTierModelDao(tierNum, tierDef.getRecordId(), usageDef.getRecordId());
        sqlDao.create(modelDao, context);
    }

    private CatalogOverrideTierDefinitionModelDao getOrCreateOverrideTierDefinitionFromTransaction(final Tier parentTier, final TierPriceOverride tierPriceOverride,Currency currency, final DateTime catalogEffectiveDate,  final Handle inTransactionHandle, final InternalCallContext context){

        final List<TieredBlockPriceOverride> resolvedTierBlockOverrides =  tierPriceOverride.getTieredBlockPriceOverrides();

        final CatalogOverrideBlockDefinitionModelDao[] overrideBlockDefinitionModelDaos = new CatalogOverrideBlockDefinitionModelDao[resolvedTierBlockOverrides.size()];
        // Loop through each tier block within a given tier (usually 1)
        for (int i = 0; i < resolvedTierBlockOverrides.size(); i++) {
            final TieredBlockPriceOverride curOverride = resolvedTierBlockOverrides.get(i);
            if (curOverride != null) {
                final CatalogOverrideBlockDefinitionModelDao createdOverrideBlockDefinitionModelDao = getOrCreateOverriddenBlockDefinitionFromTransaction(curOverride,catalogEffectiveDate, currency.name(), inTransactionHandle, context);
                overrideBlockDefinitionModelDaos[i] = createdOverrideBlockDefinitionModelDao;
            }
        }

        //
        // Given the entries (overrideBlockDefinitionModelDaos) in the catalog_override_block_definition, we look for join keys from catalog_override_tier_block
        // to see if the tier in catalog_override_tier_definition tables has already been created.
        // If we don't find them, we create both the join key entries in catalog_override_tier_block and the tier entry in catalog_override_tier_definition
        //
        final CatalogOverrideTierDefinitionSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideTierDefinitionSqlDao.class);
        final Long targetTierDefinitionRecordId = getOverrideTierDefinitionFromTransaction(overrideBlockDefinitionModelDaos, inTransactionHandle, context);
        if (targetTierDefinitionRecordId != null) {
            return sqlDao.getByRecordId(targetTierDefinitionRecordId, context);
        }

        final CatalogOverrideTierDefinitionModelDao inputTierDef = new CatalogOverrideTierDefinitionModelDao(currency.name(), null, null, catalogEffectiveDate);
        final Long recordId = sqlDao.create(inputTierDef, context);
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
        if (overrideBlockDefinitionModelDaos.length == 0) {
            return null;
        }
        // We use the first non null block, i.e. block 'i' to find the tier definition
        for (int i = 0; i < overrideBlockDefinitionModelDaos.length; i++) {
            if (overrideBlockDefinitionModelDaos[i] != null) {
                return sqlDao.getTargetTierDefinition(i, overrideBlockDefinitionModelDaos[i].getRecordId(), context);
            }
        }
        return null;
    }

    private CatalogOverrideBlockDefinitionModelDao getOrCreateOverriddenBlockDefinitionFromTransaction(TieredBlockPriceOverride tieredBlockPriceOverride,final DateTime catalogEffectiveDate, String currency, final Handle inTransactionHandle, final InternalCallContext context)
    {
        final CatalogOverrideBlockDefinitionSqlDao sqlDao = inTransactionHandle.attach(CatalogOverrideBlockDefinitionSqlDao.class);

        // If an existing block definition exists (i.e. based on the exact same attributes), we return it
        CatalogOverrideBlockDefinitionModelDao result = sqlDao.getByAttributes(tieredBlockPriceOverride.getUnitName(),
                currency, tieredBlockPriceOverride.getPrice(),
                tieredBlockPriceOverride.getMax(),
                tieredBlockPriceOverride.getSize(),
                context);
        if (result == null) {
            final CatalogOverrideBlockDefinitionModelDao blockDef = new CatalogOverrideBlockDefinitionModelDao(
                    tieredBlockPriceOverride.getUnitName(), currency, tieredBlockPriceOverride.getPrice(),
                    tieredBlockPriceOverride.getSize(),
                    tieredBlockPriceOverride.getMax(),
                    catalogEffectiveDate);
            final Long recordId = sqlDao.create(blockDef, context);
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
