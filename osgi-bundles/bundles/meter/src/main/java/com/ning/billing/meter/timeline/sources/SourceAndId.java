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

package com.ning.billing.meter.timeline.sources;

/**
 * This class represents one row in the sources table
 */
public class SourceAndId {

    private final String source;
    private final int sourceId;

    public SourceAndId(final String source, final int sourceId) {
        this.source = source;
        this.sourceId = sourceId;
    }

    public String getSource() {
        return source;
    }

    public int getSourceId() {
        return sourceId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SourceAndId");
        sb.append("{source='").append(source).append('\'');
        sb.append(", sourceId=").append(sourceId);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SourceAndId that = (SourceAndId) o;

        if (sourceId != that.sourceId) {
            return false;
        }
        if (source != null ? !source.equals(that.source) : that.source != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + sourceId;
        return result;
    }
}
