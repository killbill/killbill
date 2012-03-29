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

package com.ning.billing.catalog.overdue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.api.overdue.OverdueState;
import com.ning.billing.catalog.api.overdue.Overdueable;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultOverdueState<T extends Overdueable> implements OverdueState<T> {

    @XmlElement(required=false, name="condition")
	private DefaultCondition<T> condition;

	@XmlAttribute(required=true, name="name")
    @XmlID
    private String name; 

	@XmlElement(required=false, name="externalMessage")
	private String externalMessage = "";

	@XmlElement(required=false, name="applyCancel")
	private boolean applyCancel = false;
	
	//Other actions could include
	// - send email
	// - trigger payment retry?
	// - add tags to bundle/account
	// - set payment failure email template
	// - set payment retry interval
	// - backup payment mechanism?

	/* (non-Javadoc)
     * @see com.ning.billing.catalog.overdue.OverdueState#getStageName()
     */
	@Override
    public String getName() {
		return name;
	}

	/* (non-Javadoc)
     * @see com.ning.billing.catalog.overdue.OverdueState#getExternalMessage()
     */
	@Override
    public String getExternalMessage() {
		return externalMessage;
	}
	
	/* (non-Javadoc)
     * @see com.ning.billing.catalog.overdue.OverdueState#applyCancel()
     */
	@Override
    public boolean applyCancel() {
		return applyCancel;
	}


    protected DefaultCondition<T> getCondition() {
		return condition;
	}

	protected DefaultOverdueState<T> setName(String name) {
		this.name = name;
		return this;
	}

	protected DefaultOverdueState<T> setExternalMessage(String externalMessage) {
		this.externalMessage = externalMessage;
		return this;
	}

	protected DefaultOverdueState<T> setCancel(boolean cancel) {
		this.applyCancel = cancel;
		return this;
	}

	protected DefaultOverdueState<T> setCondition(DefaultCondition<T> condition) {
		this.condition = condition;
		return this;
	}

}
