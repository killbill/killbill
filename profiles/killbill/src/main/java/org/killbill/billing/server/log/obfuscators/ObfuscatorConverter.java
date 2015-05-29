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

import java.util.Collection;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.common.collect.ImmutableList;

/**
 * ObfuscatorConverter attempts to mask sensitive data in the log files.
 * <p/>
 * To use, define a new conversion word in your Logback configuration, e.g.:
 * <pre>
 *     <configuration>
 *         <conversionRule conversionWord="maskedMsg"
 *             converterClass="org.killbill.billing.server.log.obfuscators.ObfuscatorConverter" />
 *         <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
 *             <encoder>
 *                 <pattern>%date [%thread] - %maskedMsg%n</pattern>
 *             </encoder>
 *         </appender>
 *         <root level="DEBUG">
 *             <appender-ref ref="STDOUT" />
 *         </root>
 *     </configuration>
 * </pre>
 */
public class ObfuscatorConverter extends ClassicConverter {

    private final Collection<Obfuscator> obfuscators = ImmutableList.<Obfuscator>of(new ConfigMagicObfuscator(),
                                                                                    new PatternObfuscator(),
                                                                                    new LuhnMaskingObfuscator());

    @Override
    public String convert(final ILoggingEvent event) {
        String convertedMessage = event.getFormattedMessage();
        for (final Obfuscator obfuscator : obfuscators) {
            try {
                convertedMessage = obfuscator.obfuscate(convertedMessage, event);
            } catch (final RuntimeException e) {
                // Ignore? Not sure the impact of importing a logger here
            }
        }
        return convertedMessage;
    }
}
