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

package com.ning.billing.invoice.model;

import com.ning.billing.catalog.api.BillingPeriod;
import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;

public class TestConsole {
    public static void main(String[] args) {
        System.out.println("Starting monthly in-advance pro-ration demo");
        System.out.println();

        executeBasicTestCase();
        executeSimpleProRationTest();
        executeCancelledPlanProRationTest();
        executeCrossingYearBoundary();
        System.out.println();
    }

    private static void executeBasicTestCase() {
        System.out.println("** Starting basic test case");

        DateTime startDate = buildDateTime(2011, 1, 15);
        DateTime targetDate = buildDateTime(2011, 1, 15);
        testScenario(startDate, targetDate, 15, BillingPeriod.MONTHLY, new BigDecimal("1.0"));

        targetDate = buildDateTime(2011, 1, 23);
        testScenario(startDate,  targetDate, 15, BillingPeriod.MONTHLY, new BigDecimal("1.0"));

        targetDate = buildDateTime(2011, 2, 15);
        testScenario(startDate,  targetDate, 15, BillingPeriod.MONTHLY, new BigDecimal("2.0"));

        targetDate = buildDateTime(2011, 2, 16);
        testScenario(startDate, targetDate, 15, BillingPeriod.MONTHLY, new BigDecimal("2.0"));
    }

    private static void executeSimpleProRationTest() {
        System.out.println("** Starting pro-ration test case 1");
        System.out.println("Immediate plan change, including February");

        DateTime startDate = buildDateTime(2011, 1, 15);
        DateTime targetDate = buildDateTime(2011, 3, 3);
        testScenario(startDate, targetDate, 31, BillingPeriod.MONTHLY, new BigDecimal(2.0 + 16.0 / 31));
    }

    private static  void executeCancelledPlanProRationTest() {
        System.out.println("** Starting pro-ration test case 2");
        System.out.println("** Cancelled plan, including February, pro-ration on both sides");

        DateTime startDate = buildDateTime(2011, 1, 23);
        DateTime endDate = buildDateTime(2011, 3, 15);
        DateTime targetDate = buildDateTime(2011, 3, 16);
        testScenario(startDate, endDate, targetDate, 8, BillingPeriod.MONTHLY, new BigDecimal(16.0/31 + 1.0 + 7.0/31));
    }

    private static  void executeCrossingYearBoundary() {
        System.out.println("** Starting pro-ration test case 3");
        System.out.println("** Crossing year boundary, including leap year February");

        DateTime startDate = buildDateTime(2011, 12, 23);
        DateTime targetDate = buildDateTime(2012, 4, 1);
        testScenario(startDate, targetDate, 23, BillingPeriod.MONTHLY, new BigDecimal(4.0));
    }

    private static void testScenario(DateTime startDate, DateTime targetDate, int billingCycleDay, BillingPeriod billingPeriod, BigDecimal expectedValue) {
        BillingMode billingMode = new InAdvanceBillingMode();

        System.out.println("Start date: " + startDate.toLocalDate());
        System.out.println("Target date: " + targetDate.toLocalDate());
        System.out.println("Billing cycle day: " + billingCycleDay);
        System.out.println("Expected value: " + expectedValue.toString());
        System.out.println();

        try {
            BigDecimal numberOfBillingCycles = billingMode.calculateNumberOfBillingCycles(startDate, targetDate, billingCycleDay, billingPeriod);
            System.out.println("Result: " + numberOfBillingCycles.toString() + " billing cycles");
        } catch (InvalidDateSequenceException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            System.out.println("Invalid date sequence");
        }

        System.out.println();

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Hit <Enter> to continue");
            br.readLine();
        } catch (IOException ioex) {
            System.out.println("IO error getting input");
            System.exit(1);
        }
    }

    private static void testScenario(DateTime startDate, DateTime endDate, DateTime targetDate, int billingCycleDay, BillingPeriod billingPeriod, BigDecimal expectedValue) {
        BillingMode billingMode = new InAdvanceBillingMode();

        System.out.println("Start date: " + startDate.toLocalDate());
        System.out.println("End date: " + endDate.toLocalDate());
        System.out.println("Target date: " + targetDate.toLocalDate());
        System.out.println("Billing cycle day: " + billingCycleDay);
        System.out.println("Expected value: " + expectedValue.toString());
        System.out.println();

        try {
            BigDecimal numberOfBillingCycles = billingMode.calculateNumberOfBillingCycles(startDate, endDate, targetDate, billingCycleDay, billingPeriod);
            System.out.println("Result: " + numberOfBillingCycles.toString() + " billing cycles");
        } catch (InvalidDateSequenceException idse) {
            System.out.println("Invalid date sequence");
        }

        System.out.println();

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Hit <Enter> to continue");
            br.readLine();
        } catch (IOException ioex) {
            System.out.println("IO error getting input");
            System.exit(1);
        }
    }

    private static DateTime buildDateTime(int year, int month, int day) {
        return new DateTime(year, month, day, 0, 0, 0, 0);
    }
}