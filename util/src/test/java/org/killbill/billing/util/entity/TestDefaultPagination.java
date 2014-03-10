/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.util.entity;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.util.UtilTestSuiteNoDB;

import com.google.common.collect.ImmutableList;

public class TestDefaultPagination extends UtilTestSuiteNoDB {

    @Test(groups = "fast", description = "Test Util: Pagination builder tests")
    public void testDefaultPaginationBuilder() throws Exception {
        Assert.assertEquals(DefaultPagination.<Integer>build(0L, 0L, ImmutableList.<Integer>of()), expectedOf(0L, 0L, 0L, ImmutableList.<Integer>of()));
        Assert.assertEquals(DefaultPagination.<Integer>build(0L, 10L, ImmutableList.<Integer>of()), expectedOf(0L, 0L, 0L, ImmutableList.<Integer>of()));
        Assert.assertEquals(DefaultPagination.<Integer>build(10L, 0L, ImmutableList.<Integer>of()), expectedOf(10L, 0L, 0L, ImmutableList.<Integer>of()));
        Assert.assertEquals(DefaultPagination.<Integer>build(10L, 10L, ImmutableList.<Integer>of()), expectedOf(10L, 0L, 0L, ImmutableList.<Integer>of()));

        Assert.assertEquals(DefaultPagination.<Integer>build(0L, 0L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)), expectedOf(0L, 0L, 5L, ImmutableList.<Integer>of()));
        Assert.assertEquals(DefaultPagination.<Integer>build(0L, 10L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)), expectedOf(0L, 5L, 5L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)));
        Assert.assertEquals(DefaultPagination.<Integer>build(4L, 10L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)), expectedOf(4L, 1L, 5L, ImmutableList.<Integer>of(5)));

        Assert.assertEquals(DefaultPagination.<Integer>build(0L, 3L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)), expectedOf(0L, 3L, 5L, ImmutableList.<Integer>of(1, 2, 3)));
        Assert.assertEquals(DefaultPagination.<Integer>build(1L, 3L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)), expectedOf(1L, 3L, 5L, ImmutableList.<Integer>of(2, 3, 4)));
        Assert.assertEquals(DefaultPagination.<Integer>build(2L, 3L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)), expectedOf(2L, 3L, 5L, ImmutableList.<Integer>of(3, 4, 5)));
        Assert.assertEquals(DefaultPagination.<Integer>build(3L, 3L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)), expectedOf(3L, 2L, 5L, ImmutableList.<Integer>of(4, 5)));
        Assert.assertEquals(DefaultPagination.<Integer>build(4L, 3L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)), expectedOf(4L, 1L, 5L, ImmutableList.<Integer>of(5)));
        Assert.assertEquals(DefaultPagination.<Integer>build(5L, 3L, ImmutableList.<Integer>of(1, 2, 3, 4, 5)), expectedOf(5L, 0L, 5L, ImmutableList.<Integer>of()));
    }

    private Pagination<Integer> expectedOf(final Long currentOffset, final Long totalNbRecords,
                                           final Long maxNbRecords, final List<Integer> delegate) {
        return new DefaultPagination<Integer>(currentOffset, Long.MAX_VALUE, totalNbRecords, maxNbRecords, delegate.iterator());
    }
}
