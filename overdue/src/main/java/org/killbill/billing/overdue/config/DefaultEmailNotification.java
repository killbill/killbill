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

package org.killbill.billing.overdue.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.overdue.api.EmailNotification;

@Deprecated // Not used, just kept for config compatibility
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultEmailNotification {

    @XmlElement(required = true, name = "subject")
    private String subject;

    @XmlElement(required = true, name = "templateName")
    private String templateName;

    @XmlElement(required = false, name = "isHTML")
    private Boolean isHTML = false;
}
