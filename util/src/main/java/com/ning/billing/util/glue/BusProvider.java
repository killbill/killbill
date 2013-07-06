package com.ning.billing.util.glue;

import javax.inject.Inject;
import javax.inject.Provider;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.bus.DefaultPersistentBus;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBusConfig;
import com.ning.billing.util.clock.Clock;


public class BusProvider implements Provider<PersistentBus> {

    private IDBI dbi;
    private Clock clock;
    private PersistentBusConfig busConfig;
    private String tableName;
    private String historyTableName;

    public BusProvider(final String tableName, final String historyTableName) {
        this.tableName = tableName;
        this.historyTableName = historyTableName;
    }

    @Inject
    public void initialize(final IDBI dbi, final Clock clock, final PersistentBusConfig config) {
        this.dbi = dbi;
        this.clock = clock;
        this.busConfig = config;
    }


    @Override
    public PersistentBus get() {
        return new DefaultPersistentBus(dbi, clock, busConfig, tableName, historyTableName);
    }
}
