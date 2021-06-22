/*
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.billing.catalog.caching;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.CatalogApiException;

public class PriceOverridePattern {

    public static final String LEGACY_CUSTOM_PLAN_NAME_DELIMITER = "-";
    // In order to not collide with any expected character from XML planName, we chose one character
    // that is not allowed -- see https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName
    public static final String CUSTOM_PLAN_NAME_DELIMITER = ":";

    private final String delimiter;
    private final Pattern pattern;

    public PriceOverridePattern(final boolean useRECXMLNamesCompliant) {
        this.delimiter = useRECXMLNamesCompliant ?  CUSTOM_PLAN_NAME_DELIMITER : LEGACY_CUSTOM_PLAN_NAME_DELIMITER;
        this.pattern = Pattern.compile("(.*)" + delimiter + "(\\d+)(?:!\\d+)?$");
    }

    public String[] getPlanParts(final String planName) throws CatalogApiException  {
        final Matcher m = pattern.matcher(planName);
        if (!m.matches()) {
            throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, planName);
        }
        final String[] res = new String[2];
        res[0] = m.group(1);
        res[1] = m.group(2);
        return res;
    }

    public Pattern pattern() {
        return pattern;
    }

    public String getPlanName(final String [] parts) {
        return String.format("%s%s%s", parts[0], delimiter, parts[1]);
    }

    public boolean isOverriddenPlan(final String planName) {
        final Matcher m = pattern.matcher(planName);
        return m.matches();
    }

}
