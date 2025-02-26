/*
 * Copyright 2020-2025 Equinix, Inc
 * Copyright 2014-2025 The Billing Project, LLC
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

package org.killbill.billing.util.config.definition;

import java.util.List;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;

public interface ExportConfig extends KillbillConfig {

    @Config("org.killbill.export.extra.tables.prefix")
    @Default("aviate_catalog")
    @Description("Prefix of the extra tables that need to be imported")
    List<String> getExtraTablesPrefix();

}
