/*
 * Copyright 2017 Groupon, Inc
 * Copyright 2017 The Billing Project, LLC
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

package org.killbill.billing.util.cache;

import org.killbill.billing.util.jackson.ObjectMapper;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET;

// See http://www.cowtowncoder.com/blog/archives/2012/08/entry_477.html
public class MapperHolder {

    private static final MapperHolder instance = new MapperHolder();

    static {
        instance.mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
        instance.mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        // Stream is NOT owned by Jackson
        instance.mapper.disable(AUTO_CLOSE_TARGET);
    }

    private final SmileFactory f = new SmileFactory();
    private final ObjectMapper mapper = new ObjectMapper(f);

    public static ObjectMapper mapper() { return instance.mapper; }
}
