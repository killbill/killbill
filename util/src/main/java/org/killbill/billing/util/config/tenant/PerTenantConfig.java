/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.config.tenant;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;

public class PerTenantConfig extends HashMap<String, String> implements Externalizable {

    private static final long serialVersionUID = 3887971108446630172L;

    public PerTenantConfig() {
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        final int size = in.readInt();
        for (int i = 0; i < size; i++) {
            final Object key = in.readObject();
            final Object value = in.readObject();
            put(String.valueOf(key), value == null ? null : String.valueOf(value));
        }
    }

    @Override
    public void writeExternal(final ObjectOutput oo) throws IOException {
        oo.writeInt(size());
        for (final String key : keySet()) {
            oo.writeObject(key);
            oo.writeObject(get(key));
        }
    }
}
