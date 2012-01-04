package com.ning.billing.account.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ning.billing.account.api.Account;

public class MockAccountDao implements AccountDao {
    private final Map<String, Account> accounts = new ConcurrentHashMap<String, Account>();

    @Override
    public void save(Account entity) {
        accounts.put(entity.getId().toString(), entity);
    }

    @Override
    public Account getById(String id) {
        return accounts.get(id);
    }

    @Override
    public List<Account> get() {
        return new ArrayList<Account>(accounts.values());
    }

    @Override
    public void test() {
    }

    @Override
    public Account getAccountByKey(String key) {
        for (Account account : accounts.values()) {
            if (key.equals(account.getExternalKey())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public UUID getIdFromKey(String externalKey) {
        Account account = getAccountByKey(externalKey);
        return account == null ? null : account.getId();
    }

}
