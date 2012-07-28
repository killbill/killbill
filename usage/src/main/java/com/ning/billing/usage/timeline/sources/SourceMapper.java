package com.ning.billing.usage.timeline.sources;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class SourceMapper implements ResultSetMapper<SourceAndId> {

    @Override
    public SourceAndId map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        return new SourceAndId(r.getString("source"), r.getInt("id"));
    }
}
