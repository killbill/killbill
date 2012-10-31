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

package com.ning.billing.util.config;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.TimeSpan;

public interface UsageConfig extends KillbillConfig {

    @Config("killbill.usage.timelines.length")
    @Description("How long to buffer data in memory before flushing it to the database")
    @Default("60m")
    TimeSpan getTimelineLength();

    // This is used to predict the number of samples between two times.  It might be
    // better to store this information on a per event category basis.
    @Config("killbill.usage.timelines.pollingInterval")
    @Description("How long to between attribute polling.  This constant should be replaced by a flexible mechanism")
    @Default("30s")
    TimeSpan getPollingInterval();

    @Config("killbill.usage.timelines.performForegroundWrites")
    @Description("If true, perform database writes in the foreground; if false, in the background")
    @Default("false")
    boolean getPerformForegroundWrites();

    @Config("killbill.usage.timelines.backgroundWriteBatchSize")
    @Description("The number of TimelineChunks that must accumulate before we perform background writes, unless the max delay has been exceeded")
    @Default("1000")
    int getBackgroundWriteBatchSize();

    @Config("killbill.usage.timelines.backgroundWriteCheckInterval")
    @Description("The time interval between checks to see if we should perform background writes")
    @Default("1s")
    TimeSpan getBackgroundWriteCheckInterval();

    @Config("killbill.usage.timelines.backgroundWriteMaxDelay")
    @Description("The maximum timespan after pending chunks are added before we perform background writes")
    @Default("1m")
    TimeSpan getBackgroundWriteMaxDelay();

    @Config("killbill.usage.timelines.timelineAggregationEnabled")
    @Description("If true, periodically perform timeline aggregation; if false, don't aggregate")
    @Default("true")
    boolean getTimelineAggregationEnabled();

    @Config("killbill.usage.timelines.maxAggregationLevel")
    @Description("Max aggregation level")
    @Default("5")
    int getMaxAggregationLevel();

    @Config("killbill.usage.timelines.chunksToAggregate")
    @Description("A string with a comma-separated set of integers, one for each aggregation level, giving the number of sequential TimelineChunks with that aggregation level we must find to perform aggregation")
    // These values translate to 4 hours, 16 hours, 2.7 days, 10.7 days, 42.7 days,
    @Default("4,4,4,4,4")
    String getChunksToAggregate();

    @Config("killbill.usage.timelines.aggregationInterval")
    @Description("How often to check to see if there are timelines ready to be aggregated")
    @Default("2h")
    TimeSpan getAggregationInterval();

    @Config("killbill.usage.timelines.aggregationBatchSize")
    @Description("The number of chunks to fetch in each batch processed")
    @Default("4000")
    int getAggregationBatchSize();

    @Config("killbill.usage.timelines.aggregationSleepBetweenBatches")
    @Description("How long to sleep between aggregation batches")
    @Default("50ms")
    TimeSpan getAggregationSleepBetweenBatches();

    @Config("killbill.usage.timelines.maxChunkIdsToInvalidateOrDelete")
    @Description("If the number of queued chunkIds to invalidate or delete is greater than or equal to this count, perform aggregated timeline writes and delete or invalidate the chunks aggregated")
    @Default("1000")
    int getMaxChunkIdsToInvalidateOrDelete();

    @Config("killbill.usage.timelines.deleteAggregatedChunks")
    @Description("If true, blast the old TimelineChunk rows; if false, leave them in peace, since they won't be accessed")
    @Default("true")
    boolean getDeleteAggregatedChunks();

    @Config("killbill.usage.timelines.shutdownSaveMode")
    @Description("What to save on shut down; either all timelines (save_all_timelines) or just the accumulator start times (save_start_times)")
    @Default("save_all_timelines")
    String getShutdownSaveMode();

    @Config("killbill.usage.timelines.segmentsSize")
    @Description("Direct memory segments size in bytes to allocate when buffering incoming events")
    @Default("1048576")
    int getSegmentsSize();

    @Config("killbill.usage.timelines.maxNbSegments")
    @Description("Max number of direct memory segments to allocate. This times the number of segments indicates the max amount of data buffered before storing a copy to disk")
    @Default("10")
    int getMaxNbSegments();

    @Config("killbill.usage.timelines.spoolDir")
    @Description("Spool directory for in-memory data")
    @Default("/var/tmp/killbill")
    String getSpoolDir();
}
