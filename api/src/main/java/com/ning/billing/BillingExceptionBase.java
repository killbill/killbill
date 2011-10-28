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

package com.ning.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BillingExceptionBase extends Exception {

    private final static Logger log = LoggerFactory.getLogger(BillingExceptionBase.class);

    private static final long serialVersionUID = 165720101383L;

    private final Throwable cause;
    private final int code;
    private final String formattedMsg;

    public BillingExceptionBase(Throwable cause, ErrorCode code, final Object... args) {
        String tmp = null;
        try {
           tmp = String.format(code.getFormat(), args);
        } catch (RuntimeException e) {
            log.error("Failed to format msg for error code " + code.getCode(), e);
            throw e;
        }
        this.formattedMsg = tmp;
        this.code = code.getCode();
        this.cause = cause;
    }

    public BillingExceptionBase(ErrorCode code, final Object... args) {
        this(null, code, args);
    }

    @Override
    public String getMessage() {
        return formattedMsg;
    }

    @Override
    public Throwable getCause() {
        return cause;
    }

    public int getCode() {
        return code;
    }

}
