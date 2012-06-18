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

package com.ning.billing.util.email.templates;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Map;

import com.ning.billing.util.config.UriAccessor;
import com.ning.billing.util.io.IOUtils;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

public class MustacheTemplateEngine implements TemplateEngine {
    @Override
    public String executeTemplate(final String templateName, final Map<String, Object> data) throws IOException {
        final String templateText = getTemplateText(templateName);
        final Template template = Mustache.compiler().compile(templateText);
        return template.execute(data);
    }

    private String getTemplateText(final String templateName) throws IOException {
        final InputStream templateStream;
        try {
            templateStream = UriAccessor.accessUri(templateName);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }

        return IOUtils.toString(templateStream);
    }
}
