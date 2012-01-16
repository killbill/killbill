package com.ning.billing.payment.setup;

import com.ning.billing.payment.dao.MockPaymentDao;
import com.ning.billing.payment.dao.PaymentDao;

public class PaymentModuleWithMocks extends PaymentModule {
    @Override
    protected void installPaymentDao() {
        bind(PaymentDao.class).to(MockPaymentDao.class).asEagerSingleton();
    }
}
