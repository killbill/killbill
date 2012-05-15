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

package com.ning.billing.overdue.config.api;

public enum PaymentResponse {
    // Card issues
    INVALID_CARD("The card number, expiry date or cvc is invalid or incorrect"),
	EXPIRED_CARD("The card has expired"),
	LOST_OR_STOLEN_CARD("The card has been lost or stolen"),

	// Account issues
    DO_NOT_HONOR("Do not honor the card - usually a problem with account"),
	INSUFFICIENT_FUNDS("The account had insufficient funds to fulfil the payment"),
	DECLINE("Generic payment decline"),
	
	//Transaction
	PROCESSING_ERROR("Error processing card"),
	INVALID_AMOUNT("An invalid amount was entered"),
	DUPLICATE_TRANSACTION("A transaction with identical amount and credit card information was submitted very recently."),

	//Other
	OTHER("Some other error");
	
	private String description;
	
	private PaymentResponse(String description) {
	    this.description = description;
	}
	
	public String getDescription() {
	    return description;
	}
	
//	 690118 | Approved
//	 136956 | Do Not Honor
//	 119640 | Insufficient Funds
//	  68514 | Invalid Account Number
//	  66824 | Declined: 10417-The transaction cannot complete successfully.  Instruct the customer to use an alternative payment
//	  55473 | Declined: 10201-Agreement was canceled
//	  30930 | Pick Up Card
//	  29857 | Lost/Stolen Card
//	  28197 | Declined
//	  24830 | Declined: 10207-Transaction failed but user has alternate funding source
//	  18445 | Generic Decline
//	  18254 | Expired Card
//	  16521 | Cardholder transaction not permitted
//	  11576 | Restricted Card
//	   7410 | Account Number Does Not Match Payment Type
//	   7312 | Invalid merchant information: 10507-Payer's account is denied
//	   6425 | Invalid Transaction
//	   2825 | Declined: 10204-User's account is closed or restricted
//	   2730 | Invalid account number
//	   1331 | 
//	   1240 | Field format error: 10561-There's an error with this transaction. Please enter a complete billing address.
//	   1125 | Cardholder requested that recurring or installment payment be stopped
//	   1060 | No such issuer
//	   1047 | Issuer Unavailable
//	    816 | Not signed up for this tender type
//	    749 | Transaction not allowed at terminal
//	    663 | Invalid expiration date: 0910
//	    548 | Invalid expiration date: 1010
//	    542 | Invalid expiration date:
//	    500 | Invalid expiration date: 0810
//	    492 | Invalid expiration date: 1110
//	    410 | Invalid expiration date: 0710
//	    388 | Exceeds Approval Amount Limit
//	    362 | Generic processor error: 10001-Internal Error
//	    313 | Exceeds per transaction limit: 10553-This transaction cannot be processed.
//	    310 | Decline CVV2/CID Fail
//	    309 | Generic processor error: 10201-Agreement was canceled
//	    278 | Generic processor error: 10417-The transaction cannot complete successfully.  Instruct the customer to use an alte
//	    246 | Call Issuer
//	    237 | Generic processor error: 11091-The transaction was blocked as it would exceed the sending limit for this buyer.
//	    202 | Failed to connect to host Input Server Uri = https://payflowpro.paypal.com:443
//	    166 | Exceeds number of PIN entries
//	    150 | Invalid Amount

}
