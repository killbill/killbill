package com.ning.billing.usage.timeline.metrics;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class MetricAndIdMapper implements ResultSetMapper<MetricAndId> {

    @Override
    public MetricAndId map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        return new MetricAndId(r.getString("sample_kind"), r.getInt("sample_kind_id"));
    }
}
