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

package com.ning.billing.junction.api;

import org.joda.time.DateTime;

public interface BlockingState extends Comparable<BlockingState> {

    public abstract String getStateName();
    
    public abstract Blockable.Type getType();

    public abstract DateTime getTimestamp();

    public abstract boolean isBlockChange();

    public abstract boolean isBlockEntitlement();

    public abstract boolean isBlockBilling();

    public abstract int compareTo(BlockingState arg0);

    public abstract int hashCode();

    public abstract String getDescription();

    public abstract String toString();
}