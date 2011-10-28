/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.analytics;

import com.ning.billing.catalog.api.IDuration;
import com.ning.billing.catalog.api.TimeUnit;

public class MockDuration
{
    public static IDuration MONHTLY()
    {
        return new IDuration()
        {
            @Override
            public TimeUnit getUnit()
            {
                return TimeUnit.MONTHS;
            }

            @Override
            public int getLength()
            {
                return 1;
            }
        };
    }

    public static IDuration YEARLY()
    {
        return new IDuration()
        {
            @Override
            public TimeUnit getUnit()
            {
                return TimeUnit.YEARS;
            }

            @Override
            public int getLength()
            {
                return 1;
            }
        };
    }

    public static IDuration UNLIMITED()
    {
        return new IDuration()
        {
            @Override
            public TimeUnit getUnit()
            {
                return TimeUnit.UNLIMITED;
            }

            @Override
            public int getLength()
            {
                return 1;
            }
        };
    }
}
