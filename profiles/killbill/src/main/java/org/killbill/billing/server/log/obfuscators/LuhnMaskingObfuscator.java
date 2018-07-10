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

package org.killbill.billing.server.log.obfuscators;

import java.util.regex.Pattern;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.common.annotations.VisibleForTesting;

/**
 * LuhnMaskingObfuscator replaces sequences of digits that pass the Luhn check
 * with a masking string, leaving only the suffix containing the last four
 * digits.
 * <p/>
 * Inspired from https://github.com/esamson/logback-luhn-mask (licensed under the Apache License, Version 2.0)
 */
public class LuhnMaskingObfuscator extends Obfuscator {

    /**
     * The minimum number of digits a credit card can have.
     */
    private static final int MIN_CC_DIGITS = 13;

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    public LuhnMaskingObfuscator() {
        super();
    }

    @Override
    public String obfuscate(final String originalString, final ILoggingEvent event) {
        return mask(originalString);
    }

    private String mask(final String formattedMessage) {
        if (UUID_PATTERN.matcher(formattedMessage).matches() ||
            !hasEnoughDigits(formattedMessage)) {
            return formattedMessage;
        }

        final int length = formattedMessage.length();
        int unwrittenStart = 0;
        int numberStart = -1;
        int numberEnd;
        int digitsSeen = 0;
        final int[] last4pos = {-1, -1, -1, -1};
        int pos;
        char current;

        final StringBuilder masked = new StringBuilder(formattedMessage.length());

        for (pos = 0; pos < length; pos++) {
            current = formattedMessage.charAt(pos);
            if (isDigit(current)) {
                digitsSeen++;

                if (numberStart == -1) {
                    numberStart = pos;
                }

                last4pos[0] = last4pos[1];
                last4pos[1] = last4pos[2];
                last4pos[2] = last4pos[3];
                last4pos[3] = pos;
            } else if (digitsSeen > 0 && current != ' ' && current != '-') {
                numberEnd = last4pos[3] + 1;
                if ((digitsSeen >= MIN_CC_DIGITS)
                    && luhnCheck(stripSeparators(formattedMessage.substring(numberStart, numberEnd)))) {
                    maskCC(formattedMessage, unwrittenStart, numberStart, numberEnd, last4pos[0], masked);
                    unwrittenStart = numberEnd;
                }
                numberStart = -1;
                digitsSeen = 0;
            }
        }

        if (numberStart != -1 && (digitsSeen >= MIN_CC_DIGITS)
            && luhnCheck(stripSeparators(formattedMessage.substring(numberStart, pos)))) {
            maskCC(formattedMessage, unwrittenStart, numberStart, pos, last4pos[0], masked);
        } else {
            masked.append(formattedMessage, unwrittenStart, pos);
        }

        return masked.toString();
    }

    private void maskCC(final String formattedMessage, final int unwrittenStart, final int numberStart, final int numberEnd, final int last4pos, final StringBuilder masked) {
        masked.append(formattedMessage, unwrittenStart, numberStart);

        // Don't mask the BIN
        int binNumbersLeft = 6;
        int panStartPos = numberStart;
        char current;
        while (binNumbersLeft > 0) {
            current = formattedMessage.charAt(panStartPos);
            if (isDigit(current)) {
                masked.append(current);
                binNumbersLeft--;
            }
            panStartPos++;
        }

        // Append the mask
        masked.append(obfuscateConfidentialData(formattedMessage.substring(panStartPos, numberEnd),
                                                formattedMessage.substring(last4pos, numberEnd)));

        // Append last 4
        masked.append(formattedMessage, last4pos, numberEnd);
    }

    private boolean hasEnoughDigits(final CharSequence formattedMessage) {
        int digits = 0;
        final int length = formattedMessage.length();
        char current;

        for (int i = 0; i < length; i++) {
            current = formattedMessage.charAt(i);
            if (isDigit(current)) {
                if (++digits == MIN_CC_DIGITS) {
                    return true;
                }
            } else if (digits > 0 && current != ' ' && current != '-') {
                digits = 0;
            }
        }

        return false;
    }

    /**
     * Implementation of the [Luhn algorithm](http://en.wikipedia.org/wiki/Luhn_algorithm)
     * to check if the given string is possibly a credit card number.
     *
     * @param cardNumber the number to check. It must only contain numeric characters
     * @return `true` if the given string is a possible credit card number
     */
    @VisibleForTesting
    boolean luhnCheck(final String cardNumber) {
        int sum = 0;
        int digit, addend;
        boolean doubled = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            digit = Integer.parseInt(cardNumber.substring(i, i + 1));
            if (doubled) {
                addend = digit * 2;
                if (addend > 9) {
                    addend -= 9;
                }
            } else {
                addend = digit;
            }
            sum += addend;
            doubled = !doubled;
        }
        return (sum % 10) == 0;
    }

    /**
     * Remove any ` ` and `-` characters from the given string.
     *
     * @param cardNumber the number to clean up
     * @return if the given string contains no ` ` or `-` characters, the string
     * itself is returned, otherwise a new string containing no ` ` or `-`
     * characters is returned
     */
    @VisibleForTesting
    String stripSeparators(final String cardNumber) {
        final int length = cardNumber.length();
        final char[] result = new char[length];
        int count = 0;
        char cur;
        for (int i = 0; i < length; i++) {
            cur = cardNumber.charAt(i);
            if (!(cur == ' ' || cur == '-')) {
                result[count++] = cur;
            }
        }
        if (count == length) {
            return cardNumber;
        }
        return new String(result, 0, count);
    }

    private static boolean isDigit(final char c) {
        switch (c) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return true;
            default:
                return false;
        }
    }
}
