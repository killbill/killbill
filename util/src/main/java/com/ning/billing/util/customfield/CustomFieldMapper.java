package com.ning.billing.util.customfield;

import com.ning.billing.util.entity.MapperBase;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class CustomFieldMapper extends MapperBase implements ResultSetMapper<CustomField> {
    @Override
    public CustomField map(int index, ResultSet result, StatementContext context) throws SQLException {
        UUID id = UUID.fromString(result.getString("id"));
        String fieldName = result.getString("field_name");
        String fieldValue = result.getString("field_value");
        String createdBy = result.getString("created_by");
        DateTime createdDate = getDate(result, "created_date");
        String updatedBy = result.getString("updated_by");
        DateTime updatedDate = getDate(result, "updated_date");
        return new StringCustomField(id, createdBy, createdDate, updatedBy, updatedDate, fieldName, fieldValue);
    }
}
