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

package org.killbill.billing.util.security.shiro.realm;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkipSSLCheckSocketFactory extends SocketFactory {

    private static final Logger log = LoggerFactory.getLogger(SkipSSLCheckSocketFactory.class);

    private static SocketFactory skipSSLCheckFactory = null;

    static {
        final TrustManager[] noOpTrustManagers = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }

                    public void checkClientTrusted(X509Certificate[] c, String a) { }

                    public void checkServerTrusted(X509Certificate[] c, String a) { }
                }};

        try {
            final SSLContext context = SSLContext.getInstance("SSL");
            context.init(null, noOpTrustManagers, new SecureRandom());
            skipSSLCheckFactory = context.getSocketFactory();
        } catch (GeneralSecurityException e) {
            log.warn("SSL exception", e);
        }
    }

    public static SocketFactory getDefault() {
        return new SkipSSLCheckSocketFactory();
    }

    public Socket createSocket(final String arg0, final int arg1) throws IOException {
        return skipSSLCheckFactory.createSocket(arg0, arg1);
    }

    public Socket createSocket(final InetAddress arg0, final int arg1) throws IOException {
        return skipSSLCheckFactory.createSocket(arg0, arg1);
    }

    public Socket createSocket(final String arg0, final int arg1, final InetAddress arg2, final int arg3) throws IOException {
        return skipSSLCheckFactory.createSocket(arg0, arg1, arg2, arg3);
    }

    public Socket createSocket(final InetAddress arg0, final int arg1, final InetAddress arg2, final int arg3) throws IOException {
        return skipSSLCheckFactory.createSocket(arg0, arg1, arg2, arg3);
    }
}
