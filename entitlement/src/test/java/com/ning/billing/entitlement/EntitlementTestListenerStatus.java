package com.ning.billing.entitlement;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.api.TestListenerStatus;

public class EntitlementTestListenerStatus implements TestListenerStatus {

    private final Logger log = LoggerFactory.getLogger(EntitlementTestListenerStatus.class);

    private boolean isListenerFailed;
    private String listenerFailedMsg;


    @Inject
    public EntitlementTestListenerStatus() {
        isListenerFailed = false;
    }


    @Override
    public void failed(final String msg) {
        this.isListenerFailed = true;
        this.listenerFailedMsg = msg;
    }

    @Override
    public void resetTestListenerStatus() {
        this.isListenerFailed = false;
        this.listenerFailedMsg = null;
    }

    public void assertListenerStatus() {
        if (isListenerFailed) {
            log.error(listenerFailedMsg);
            Assert.fail(listenerFailedMsg);
        }
    }
}
