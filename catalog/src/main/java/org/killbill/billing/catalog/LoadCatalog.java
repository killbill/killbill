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

package org.killbill.billing.catalog;

import java.io.File;

import org.killbill.xmlloader.XMLLoader;

public class LoadCatalog {
    public static void main(final String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: <catalog filepath>");
            System.exit(0);
        }
        File file = new File(args[0]);
        if(!file.exists()) {
            System.err.println("Error: '" + args[0] + "' does not exist");
        }
        try {
            XMLLoader.getObjectFromUri(file.toURI(), StandaloneCatalog.class);
            System.out.println("Success: Catalog loads!");
        } catch (Exception e) {
            System.err.println(String.format("Error: Cannot load %s because: %s", file.getName(), e));
        }
    }

}
