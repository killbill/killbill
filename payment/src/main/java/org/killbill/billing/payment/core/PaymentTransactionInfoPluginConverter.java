/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.core;

import javax.annotation.Nullable;

import org.killbill.automaton.OperationResult;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;

//
// Conversion between the plugin result to the payment state and transaction status
//
public class PaymentTransactionInfoPluginConverter {


    public static TransactionStatus toTransactionStatus(@Nullable final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin) {
        //
        // A null paymentTransactionInfoPlugin means that plugin threw a PluginApiException to indicate it knew for sure
        // the transaction did not occur, so we can safely transition into PLUGIN_FAILURE
        //
        if (paymentTransactionInfoPlugin == null) {
            return TransactionStatus.PLUGIN_FAILURE;
        }

        switch (paymentTransactionInfoPlugin.getStatus()) {
            case PROCESSED:
                return TransactionStatus.SUCCESS;
            case PENDING:
                return TransactionStatus.PENDING;
            // The naming is a bit inconsistent, but ERROR on the plugin side means PAYMENT_FAILURE (that is a case where transaction went through but did not
            // return successfully (e.g: CC denied, ...)
            case ERROR:
                return TransactionStatus.PAYMENT_FAILURE;
            //
            // This will be picked up by Janitor to figure out what really happened and correct the state if needed
            // Note that the default case includes the null status
            //
            case UNDEFINED:
            default:
                return TransactionStatus.UNKNOWN;
        }
    }

    public static OperationResult toOperationResult(@Nullable final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin) {
        if (paymentTransactionInfoPlugin == null || paymentTransactionInfoPlugin.getStatus() == null) {
            return OperationResult.EXCEPTION;
        }

        switch (paymentTransactionInfoPlugin.getStatus()) {
            case PROCESSED:
                return OperationResult.SUCCESS;
            case PENDING:
                return OperationResult.PENDING;
            case ERROR:
                return OperationResult.FAILURE;
            // Might change later if janitor fixes the state
            case UNDEFINED:
            default:
                return OperationResult.EXCEPTION;
        }
    }
}
