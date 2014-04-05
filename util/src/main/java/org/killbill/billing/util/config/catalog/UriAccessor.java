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

package org.killbill.billing.util.config.catalog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.io.Resources;

public class UriAccessor {

    private static final String URI_SCHEME_FOR_ARCHIVE_FILE = "jar:file";
    private static final String URI_SCHEME_FOR_CLASSPATH = "jar";
    private static final String URI_SCHEME_FOR_FILE = "file";

    public static InputStream accessUri(final String uri) throws IOException, URISyntaxException {
        return accessUri(new URI(uri));
    }

    public static InputStream accessUri(URI uri) throws IOException, URISyntaxException {
        final String scheme = uri.getScheme();

        final URL url;
        if (scheme == null) {
            uri = new URI(Resources.getResource(uri.toString()).toExternalForm());
        } else if (scheme.equals(URI_SCHEME_FOR_CLASSPATH)) {
            if (uri.toString().startsWith(URI_SCHEME_FOR_ARCHIVE_FILE)) {
                return getInputStreamFromJarFile(uri.toString());
            } else {
                return UriAccessor.class.getResourceAsStream(uri.getPath());
            }
        } else if (scheme.equals(URI_SCHEME_FOR_FILE) &&
                   !uri.getSchemeSpecificPart().startsWith("/")) { // interpret URIs of this form as relative path uris
            uri = new File(uri.getSchemeSpecificPart()).toURI();
        }
        url = uri.toURL();
        return url.openConnection().getInputStream();
    }

    /**
     *
     * @param classPathFile of the form jar:file:/path!/resource
     * @return
     * @throws IOException if fail to extract InputStream
     */
    private static InputStream getInputStreamFromJarFile(final String classPathFile) throws IOException {

        final String[] partsPathAndResource = classPathFile.split("!");
        final String resourceInJar = partsPathAndResource[1].substring(1);

        final String[] partsColumns = partsPathAndResource[0].split(":");
        final String jarFileName = partsColumns[2];

        return new ZipFile(new File(jarFileName)).getInputStream(new ZipEntry(resourceInJar));
    }

    public static String accessUriAsString(final String uri) throws IOException, URISyntaxException {
        return accessUriAsString(new URI(uri));
    }

    public static String accessUriAsString(final URI uri) throws IOException, URISyntaxException {
        final InputStream stream = accessUri(uri);
        return new Scanner(stream).useDelimiter("\\A").next();
    }
}
