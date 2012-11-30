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

package com.ning.billing.meter.timeline.shutdown;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.meter.timeline.util.DateTimeUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StartTimesMapper implements ResultSetMapper<StartTimes> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public StartTimes map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        try {
            return new StartTimes(DateTimeUtils.dateTimeFromUnixSeconds(r.getInt("time_inserted")),
                                  (Map<Integer, Map<Integer, DateTime>>) mapper.readValue(r.getBlob("start_times").getBinaryStream(), new TypeReference<Map<Integer, Map<Integer, DateTime>>>() {
                                  }));
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not decode the StartTimes map"), e);
        }
    }
}
