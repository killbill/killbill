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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;

// See http://www.cowtowncoder.com/blog/archives/2012/08/entry_477.html
public class ExternalizableInput extends InputStream {

    private final ObjectInput in;

    public ExternalizableInput(final ObjectInput in) {
        this.in = in;
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public int read(final byte[] buffer) throws IOException {
        return in.read(buffer);
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int len) throws IOException {
        return in.read(buffer, offset, len);
    }

    @Override
    public long skip(final long n) throws IOException {
        return in.skip(n);
    }
}
