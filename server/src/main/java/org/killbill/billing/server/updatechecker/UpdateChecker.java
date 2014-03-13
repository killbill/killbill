/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.server.updatechecker;

import java.io.IOException;

import javax.servlet.ServletContext;

import org.killbill.billing.server.config.UpdateCheckConfig;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(UpdateChecker.class);

    private final ConfigSource configSource;
    private final UpdateCheckConfig config;

    public UpdateChecker(final ConfigSource configSource) {
        this.configSource = configSource;
        this.config = new ConfigurationObjectFactory(configSource).build(UpdateCheckConfig.class);
    }

    public void check(final ServletContext servletContext) {
        log.info("For Kill Bill Commercial Support, visit http://thebillingproject.com or send an email to support@thebillingproject.com");

        if (shouldSkipUpdateCheck()) {
            return;
        }

        final Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    doCheck(servletContext);
                } catch (final IOException e) {
                    // Don't pollute logs, maybe no internet access?
                    log.debug("Unable to perform update check", e);
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    private void doCheck(final ServletContext servletContext) throws IOException {
        // Information about this version of Kill Bill
        final ProductInfo productInfo = new ProductInfo();
        // Information about other versions of Kill Bill
        final UpdateListProperties updateListProperties = new UpdateListProperties(config.updateCheckURL().toURL(), config.updateCheckConnectionTimeout());

        // Log generic information about Kill Bill
        if (updateListProperties.getGeneralNotice() != null) {
            log.info(updateListProperties.getGeneralNotice());
        }

        // Log generic information about this release
        if (updateListProperties.getNoticeForVersion(productInfo.getVersion()) != null) {
            log.info(updateListProperties.getNoticeForVersion(productInfo.getVersion()));
        }

        // Log if there is a new version of Kill Bill available
        final StringBuilder updates = new StringBuilder();
        for (final String update : updateListProperties.getUpdatesForVersion(productInfo.getVersion())) {
            if (updates.length() > 0) {
                updates.append(", ");
            }

            updates.append(update);
            final String changeLog = updateListProperties.getReleaseNotesForVersion(update);
            if (changeLog != null) {
                updates.append(" [").append(changeLog).append("]");
            }
        }
        if (updates.length() > 0) {
            log.info("New update(s) found: " + updates.toString() + ". Please check http://kill-bill.org for the latest version.");
        }

        // Send anonymous data
        final Tracker tracker = new Tracker(configSource, productInfo, servletContext);
        tracker.track();
    }

    private boolean shouldSkipUpdateCheck() {
        if (config.shouldSkipUpdateCheck()) {
            return true;
        }

        try {
            Class.forName("org.testng.Assert");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }
}
