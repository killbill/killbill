package com.ning.billing.account.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockAccountUserApi implements IAccountUserApi {
    private final CopyOnWriteArrayList<IAccount> accounts = new CopyOnWriteArrayList<IAccount>();

    @Override
    public IAccount createAccount(IAccountData data) {
        IAccount result = new Account().withKey(data.getKey())
                                       .withName(data.getName())
                                       .withEmail(data.getEmail())
                                       .withPhone(data.getPhone())
                                       .withBillCycleDay(data.getBillCycleDay())
                                       .withCurrency(data.getCurrency());
        accounts.add(result);
        return result;
    }

    @Override
    public IAccount getAccountByKey(String key) {
        for (IAccount account : accounts) {
            if (key.equals(account.getKey())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public IAccount getAccountFromId(UUID uid) {
        for (IAccount account : accounts) {
            if (uid.equals(account.getId())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public List<IAccount> getAccounts() {
        return new ArrayList<IAccount>(accounts);
    }

}
