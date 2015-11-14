/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.util.nodes.dao;

import org.joda.time.DateTime;

public class NodeInfoModelDao {

    private Long recordId;
    private String nodeName;
    private DateTime bootDate;
    private DateTime updatedDate;
    private String nodeInfo;
    private Boolean isActive;

    public NodeInfoModelDao() {
    }

    public NodeInfoModelDao(final Long recordId,
                            final String nodeName,
                            final DateTime bootDate,
                            final DateTime updatedDate,
                            final String nodeInfo,
                            final Boolean isActive) {
        this.recordId = recordId;
        this.nodeName = nodeName;
        this.bootDate = bootDate;
        this.updatedDate = updatedDate;
        this.nodeInfo = nodeInfo;
        this.isActive = isActive;
    }

    public NodeInfoModelDao(final String nodeName,
                            final DateTime bootDate,
                            final String nodeInfo) {
        this(-1L, nodeName, bootDate, bootDate, nodeInfo, true);
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(final Long recordId) {
        this.recordId = recordId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public DateTime getBootDate() {
        return bootDate;
    }

    public void setBootDate(final DateTime bootDate) {
        this.bootDate = bootDate;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getNodeInfo() {
        return nodeInfo;
    }

    public void setNodeInfo(final String nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    public void setIsActive(final Boolean isActive) {
        this.isActive = isActive;
    }

    // TODO  Required for making the BindBeanFactory with Introspector work
    public Boolean getIsActive() {
        return isActive;
    }

    public Boolean isActive() {
        return isActive;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeInfoModelDao)) {
            return false;
        }

        final NodeInfoModelDao that = (NodeInfoModelDao) o;

        /*
        if (recordId != null ? !recordId.equals(that.recordId) : that.recordId != null) {
            return false;
        }
        */
        if (nodeName != null ? !nodeName.equals(that.nodeName) : that.nodeName != null) {
            return false;
        }
        if (bootDate != null ? bootDate.compareTo(that.bootDate) != 0 : that.bootDate != null) {
            return false;
        }
        /*
        if (updatedDate != null ? updatedDate.compareTo(that.updatedDate) != 0 : that.updatedDate != null) {
            return false;
        }
        */
        if (nodeInfo != null ? !nodeInfo.equals(that.nodeInfo) : that.nodeInfo != null) {
            return false;
        }
        return !(isActive != null ? !isActive.equals(that.isActive) : that.isActive != null);

    }

    @Override
    public int hashCode() {
        /* int result = recordId != null ? recordId.hashCode() : 0; */
        int result = nodeName != null ? nodeName.hashCode() : 0;
        result = 31 * result + (bootDate != null ? bootDate.hashCode() : 0);
        //result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        result = 31 * result + (nodeInfo != null ? nodeInfo.hashCode() : 0);
        result = 31 * result + (isActive != null ? isActive.hashCode() : 0);
        return result;
    }
}
