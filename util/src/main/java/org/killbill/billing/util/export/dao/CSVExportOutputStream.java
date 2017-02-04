/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.export.dao;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.killbill.billing.util.api.ColumnInfo;
import org.killbill.billing.util.api.DatabaseExportOutputStream;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.csv.CsvSchema.ColumnType;

public class CSVExportOutputStream extends OutputStream implements DatabaseExportOutputStream {

    private static final CsvMapper mapper = new CsvMapper();

    private final OutputStream delegate;

    private String currentTableName;
    private CsvSchema currentCSVSchema;
    private ObjectWriter writer;
    private boolean shouldWriteHeader = false;

    public CSVExportOutputStream(final OutputStream delegate) {
        this.delegate = delegate;

        // To be mysqlimport friendly with datetime type
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Override
    public void write(final int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public void newTable(final String tableName, final List<ColumnInfo> columnsForTable) {
        currentTableName = tableName;

        final CsvSchema.Builder builder = CsvSchema.builder();
        // Remove quoting of character which applies (somewhat arbitrarily, Tatu???) for string whose length is greater than MAX_QUOTE_CHECK = 24 -- See CVSWriter#_mayNeedQuotes
        builder.disableQuoteChar();

        builder.setColumnSeparator('|');

        for (final ColumnInfo columnInfo : columnsForTable) {
            builder.addColumn(columnInfo.getColumnName(), getColumnTypeFromSqlType(columnInfo.getDataType()));
        }
        currentCSVSchema = builder.build();

        writer = mapper.writer(currentCSVSchema);
        shouldWriteHeader = true;
    }

    @Override
    public void write(final Map<String, Object> row) throws IOException {
        final byte[] bytes;
        if (shouldWriteHeader) {
            // Write the header once (mapper.writer will clone the writer). Add a small marker in front of the header
            // to easily split it
            write(String.format("-- %s ", currentTableName).getBytes());
            bytes = mapper.writer(currentCSVSchema.withHeader()).writeValueAsBytes(row);
            shouldWriteHeader = false;
        } else {
            bytes = writer.writeValueAsBytes(row);
        }

        write(bytes);
    }

    private ColumnType getColumnTypeFromSqlType(final String dataType) {
        if (dataType == null) {
            return ColumnType.STRING;
        } else if ("bigint".equals(dataType)) {
            return ColumnType.NUMBER_OR_STRING;
        } else if ("blob".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("char".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("date".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("datetime".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("decimal".equals(dataType)) {
            return ColumnType.NUMBER_OR_STRING;
        } else if ("enum".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("int".equals(dataType)) {
            return ColumnType.NUMBER_OR_STRING;
        } else if ("longblob".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("longtext".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("mediumblob".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("mediumtext".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("set".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("smallint".equals(dataType)) {
            return ColumnType.NUMBER_OR_STRING;
        } else if ("text".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("time".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("timestamp".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("tinyint".equals(dataType)) {
            return ColumnType.NUMBER_OR_STRING;
        } else if ("varbinary".equals(dataType)) {
            return ColumnType.STRING;
        } else if ("varchar".equals(dataType)) {
            return ColumnType.STRING;
        } else {
            return ColumnType.STRING;
        }
    }
}
