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

package com.ning.billing.account.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ning.billing.util.callcontext.CallContext;
import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;

public class MockAccountUserApi implements AccountUserApi {
    private final CopyOnWriteArrayList<Account> accounts = new CopyOnWriteArrayList<Account>();

    public Account createAccount(UUID id,
                                 String externalKey,
                                 String email,
                                 String name,
                                 int firstNameLength,
                                 Currency currency,
                                 int billCycleDay,
                                 String paymentProviderName,
                                 final DateTimeZone timeZone, 
                                 final String locale,
                                 final String address1, 
                                 final String address2, 
                                 final String companyName,
                                 final String city,
                                 final String stateOrProvince, 
                                 final String country, 
                                 final String postalCode, 
                                 final String phone) {

		Account result = new DefaultAccount(id, externalKey, email, name,
				firstNameLength, currency, billCycleDay, paymentProviderName,
				timeZone, locale, address1, address2, companyName, city,
				stateOrProvince, country, postalCode, phone, null, null, null, null);
		accounts.add(result);
		return result;
	}

    @Override
    public Account createAccount(final AccountData data, final List<CustomField> fields,
                                 final List<Tag> tags, final CallContext context) throws AccountApiException {
        Account result = new DefaultAccount(data);
        accounts.add(result);
        return result;
    }

    @Override
    public Account getAccountByKey(String key) {
        for (Account account : accounts) {
            if (key.equals(account.getExternalKey())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public Account getAccountById(UUID uid) {
        for (Account account : accounts) {
            if (uid.equals(account.getId())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public List<Account> getAccounts() {
        return new ArrayList<Account>(accounts);
    }

    @Override
    public UUID getIdFromKey(String externalKey) {
        for (Account account : accounts) {
            if (externalKey.equals(account.getExternalKey())) {
                return account.getId();
            }
        }
        return null;
    }

    @Override
    public void updateAccount(final Account account, final CallContext context) {
        throw new UnsupportedOperationException();
    }

	@Override
	public void deleteAccountByKey(final String externalKey, final CallContext context)
			throws AccountApiException {
		for (Account account : accounts) {
            if (externalKey.equals(account.getExternalKey())) {
                accounts.remove(account);
            }
        }	
		
	}

	@Override
	public Account migrateAccount(final MigrationAccountData data,
			final List<CustomField> fields, final List<Tag> tags, final CallContext context)
			throws AccountApiException {
		Account result = new DefaultAccount(data);
        accounts.add(result);
        return result;
	}

	@Override
	public void updateAccount(String key, AccountData accountData, final CallContext context)
			throws AccountApiException {
		throw new UnsupportedOperationException();
	}
}
