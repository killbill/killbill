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

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;

public class MustacheTemplateEngine implements TemplateEngine {
    @Override
    public String executeTemplate(String templateName, Map<String, Object> data) throws IOException {
        String templateText = getTemplateText(templateName);
        Template template = Mustache.compiler().compile(templateText);
        return template.execute(data);
    }

    private String getTemplateText(String templateName) throws IOException {
        InputStream templateStream = this.getClass().getResourceAsStream(templateName + ".mustache");
        StringWriter writer = new StringWriter();
        IOUtils.copy(templateStream, writer, "UTF-8");
        return writer.toString();
    }
}
