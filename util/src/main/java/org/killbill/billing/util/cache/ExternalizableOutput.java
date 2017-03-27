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
import java.io.ObjectOutput;
import java.io.OutputStream;

// See http://www.cowtowncoder.com/blog/archives/2012/08/entry_477.html
public class ExternalizableOutput extends OutputStream {

    private final ObjectOutput out;

    public ExternalizableOutput(final ObjectOutput out) {
        this.out = out;
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public void write(final int ch) throws IOException {
        out.write(ch);
    }

    @Override
    public void write(final byte[] data) throws IOException {
        out.write(data);
    }

    @Override
    public void write(final byte[] data, final int offset, final int len) throws IOException {
        out.write(data, offset, len);
    }
}
