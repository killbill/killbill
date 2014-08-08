/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.server.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.Discriminator;

public class ThreadNameBasedDiscriminator implements Discriminator<ILoggingEvent> {

    private static final String KEY = "threadName";
    private static final String ORG_KILLBILL_DOT = "org.killbill.";
    private static final String BILLING_DOT = "billing.";
    private static final String COMMONS_DOT = "commons.";

    private final KillbillSecurityManager killbillSecurityManager = new KillbillSecurityManager();

    private boolean started;

    @Override
    public String getDiscriminatingValue(final ILoggingEvent iLoggingEvent) {
        final String element = getKillbillCaller();
        if (element != null) {
            return element;
        } else {
            return Thread.currentThread().getName();
        }
    }

    @Override
    public String getKey() {
        return KEY;
    }

    public void start() {
        started = true;
    }

    public void stop() {
        started = false;
    }

    public boolean isStarted() {
        return started;
    }

    private String getKillbillCaller() {
        final Class[] stackTrace = killbillSecurityManager.getClassContext();
        if (stackTrace == null || stackTrace.length <= 3) {
            return null;
        }

        // Skip first ones (i.e. skip this class)
        for (int i = 4; i < stackTrace.length; i++) {
            final Class aStackTrace = stackTrace[i];
            final String className = aStackTrace.getName();
            final char[] classNameChars = className.toCharArray();

            // Try to be faster than using patterns or String methods
            if (classNameChars.length > 13 &&
                classNameChars[0] == 'o' &&
                classNameChars[1] == 'r' &&
                classNameChars[2] == 'g' &&
                classNameChars[3] == '.' &&
                classNameChars[4] == 'k' &&
                classNameChars[5] == 'i' &&
                classNameChars[6] == 'l' &&
                classNameChars[7] == 'l' &&
                classNameChars[8] == 'b' &&
                classNameChars[9] == 'i' &&
                classNameChars[10] == 'l' &&
                classNameChars[11] == 'l' &&
                classNameChars[12] == '.') {
                String markerName = ORG_KILLBILL_DOT;

                // Extract the killbill module for Kill Bill proper calls, otherwise get the top-level package
                int startPosition = 13;
                if (classNameChars.length > 21 &&
                    classNameChars[13] == 'b' &&
                    classNameChars[14] == 'i' &&
                    classNameChars[15] == 'l' &&
                    classNameChars[16] == 'l' &&
                    classNameChars[17] == 'i' &&
                    classNameChars[18] == 'n' &&
                    classNameChars[19] == 'g' &&
                    classNameChars[20] == '.') {
                    startPosition = 21;
                    markerName += BILLING_DOT;

                    // Extract the submodule in util
                    if (classNameChars.length > 26 &&
                        classNameChars[21] == 'u' &&
                        classNameChars[22] == 't' &&
                        classNameChars[23] == 'i' &&
                        classNameChars[24] == 'l' &&
                        classNameChars[25] == '.') {
                        startPosition = 26;

                        // Skip JDBI wrappers
                        if (classNameChars.length > 33 &&
                            classNameChars[26] == 'e' &&
                            classNameChars[27] == 'n' &&
                            classNameChars[28] == 't' &&
                            classNameChars[29] == 'i' &&
                            classNameChars[30] == 't' &&
                            classNameChars[31] == 'y' &&
                            classNameChars[32] == '.') {
                            continue;
                        } else if (classNameChars.length > 30 &&
                                   classNameChars[26] == 'd' &&
                                   classNameChars[27] == 'a' &&
                                   classNameChars[28] == 'o' &&
                                   classNameChars[29] == '.') {
                            continue;
                        }
                    }
                    // Extract the submodule in commons
                } else if (classNameChars.length > 21 &&
                           classNameChars[13] == 'c' &&
                           classNameChars[14] == 'o' &&
                           classNameChars[15] == 'm' &&
                           classNameChars[16] == 'm' &&
                           classNameChars[17] == 'o' &&
                           classNameChars[18] == 'n' &&
                           classNameChars[19] == 's' &&
                           classNameChars[20] == '.') {
                    startPosition = 21;
                    markerName += COMMONS_DOT;

                    // Skip profiling
                    if (classNameChars.length > 31 &&
                        classNameChars[21] == 'p' &&
                        classNameChars[22] == 'r' &&
                        classNameChars[23] == 'o' &&
                        classNameChars[24] == 'f' &&
                        classNameChars[25] == 'i' &&
                        classNameChars[26] == 'l' &&
                        classNameChars[27] == 'i' &&
                        classNameChars[28] == 'n' &&
                        classNameChars[29] == 'g' &&
                        classNameChars[30] == '.') {
                        continue;
                        // Skip JDBI utilities
                    } else if (classNameChars.length > 26 &&
                               classNameChars[21] == 'j' &&
                               classNameChars[22] == 'd' &&
                               classNameChars[23] == 'b' &&
                               classNameChars[24] == 'i' &&
                               classNameChars[25] == '.') {
                        continue;
                    }
                }

                for (int j = startPosition; j < classNameChars.length; j++) {
                    final char kar = classNameChars[j];
                    if (kar == '.') {
                        break;
                    }
                    markerName += kar;
                }
                return markerName;
            }
        }

        return null;
    }

    // Simple utility class used to provide public access to the protected getClassContext() method of SecurityManager.
    // Faster than new Throwable().getStackTrace() and Thread.currentThread().getStackTrace()
    private static final class KillbillSecurityManager extends SecurityManager {

        public Class[] getClassContext() {
            return super.getClassContext();
        }
    }
}
