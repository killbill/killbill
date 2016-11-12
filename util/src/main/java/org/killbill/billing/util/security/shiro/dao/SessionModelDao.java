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

package org.killbill.billing.util.security.shiro.dao;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.shiro.io.DefaultSerializer;
import org.apache.shiro.io.Serializer;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.SimpleSession;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class SessionModelDao {

    private final Serializer<Map> serializer = new DefaultSerializer<Map>();

    private Long recordId;
    private String id;
    private DateTime startTimestamp;
    private DateTime lastAccessTime;
    private long timeout;
    private String host;
    private byte[] sessionData;

    public SessionModelDao() { /* For the DAO mapper */ }

    public SessionModelDao(final Session session) {
        this.id = session.getId() == null ? null : session.getId().toString();
        this.startTimestamp = new DateTime(session.getStartTimestamp(), DateTimeZone.UTC);
        this.lastAccessTime = new DateTime(session.getLastAccessTime(), DateTimeZone.UTC);
        this.timeout = session.getTimeout();
        this.host = session.getHost();
        try {
            this.sessionData = serializeSessionData(session);
        } catch (final IOException e) {
            this.sessionData = new byte[]{};
        }
    }

    public Session toSimpleSession() throws IOException {
        final SimpleSession simpleSession = new SimpleSession();
        if (id != null) {
            // Make sure to use a String here! It will be used as-is as the key in Ehcache.
            // When retrieving the session, the sessionId will be a String
            // See https://github.com/killbill/killbill/issues/299
            simpleSession.setId(id);
        }
        simpleSession.setStartTimestamp(startTimestamp.toDate());
        simpleSession.setLastAccessTime(lastAccessTime.toDate());
        simpleSession.setTimeout(timeout);
        simpleSession.setHost(host);

        final Map attributes = serializer.deserialize(sessionData);
        //noinspection unchecked
        simpleSession.setAttributes(attributes);

        return simpleSession;
    }

    public Long getRecordId() {
        return recordId;
    }

    public String getId() {
        return id;
    }

    public DateTime getStartTimestamp() {
        return startTimestamp;
    }

    public DateTime getLastAccessTime() {
        return lastAccessTime;
    }

    public long getTimeout() {
        return timeout;
    }

    public String getHost() {
        return host;
    }

    public byte[] getSessionData() {
        return sessionData;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SessionModelDao{");
        sb.append("recordId=").append(recordId);
        sb.append(", id='").append(id).append('\'');
        sb.append(", startTimestamp=").append(startTimestamp);
        sb.append(", lastAccessTime=").append(lastAccessTime);
        sb.append(", timeout=").append(timeout);
        sb.append(", host='").append(host).append('\'');
        sb.append(", sessionData=").append(Arrays.toString(sessionData));
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

        final SessionModelDao that = (SessionModelDao) o;

        if (timeout != that.timeout) {
            return false;
        }
        if (recordId != null ? !recordId.equals(that.recordId) : that.recordId != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (startTimestamp != null ? !startTimestamp.equals(that.startTimestamp) : that.startTimestamp != null) {
            return false;
        }
        if (lastAccessTime != null ? !lastAccessTime.equals(that.lastAccessTime) : that.lastAccessTime != null) {
            return false;
        }
        if (host != null ? !host.equals(that.host) : that.host != null) {
            return false;
        }
        return Arrays.equals(sessionData, that.sessionData);

    }

    @Override
    public int hashCode() {
        int result = recordId != null ? recordId.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (startTimestamp != null ? startTimestamp.hashCode() : 0);
        result = 31 * result + (lastAccessTime != null ? lastAccessTime.hashCode() : 0);
        result = 31 * result + (int) (timeout ^ (timeout >>> 32));
        result = 31 * result + (host != null ? host.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(sessionData);
        return result;
    }

    private byte[] serializeSessionData(final Session session) throws IOException {
        final Map<Object, Object> sessionAttributes = new HashMap<Object, Object>();
        for (final Object key : session.getAttributeKeys()) {
            sessionAttributes.put(key, session.getAttribute(key));
        }

        return serializer.serialize(sessionAttributes);
    }
}
