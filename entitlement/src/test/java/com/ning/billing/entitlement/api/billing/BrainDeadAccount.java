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

package com.ning.billing.entitlement.api.billing;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

public class BrainDeadAccount implements Account {

	@Override
	public String getExternalKey() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getName() {
		throw new UnsupportedOperationException();	}

	@Override
	public int getFirstNameLength() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getEmail() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getPhone() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getBillCycleDay() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Currency getCurrency() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getPaymentProviderName() {
		throw new UnsupportedOperationException();
	}

    @Override
    public DateTimeZone getTimeZone() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLocale() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAddress1() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAddress2() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCompanyName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCity() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getStateOrProvince() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPostalCode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCountry() {
        throw new UnsupportedOperationException();
    }

    @Override
	public String getFieldValue(String fieldName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setFieldValue(String fieldName, String fieldValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<CustomField> getFieldList() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clearFields() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getObjectName() {
		throw new UnsupportedOperationException();
	}

	@Override
	public UUID getId() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Tag> getTagList() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasTag(String tagName) {
		throw new UnsupportedOperationException();
	}

    @Override
    public void addTag(final TagDefinition definition, final String addedBy, final DateTime dateAdded) {
        throw new UnsupportedOperationException();
    }

    @Override
	public void addTags(List<Tag> tags) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clearTags() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeTag(TagDefinition definition) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean generateInvoice() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean processPayment() {
		throw new UnsupportedOperationException();
	}
	@Override
	public void addFields(List<CustomField> fields) {
		throw new UnsupportedOperationException();
		
	}

}
