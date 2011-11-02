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

package com.ning.billing.catalog.io;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import com.ning.billing.catalog.Catalog;

public class XMLSchemaGenerator {

	//Note: this main method is called by the maven build to generate the schema for the jar
	public static void main(String[] args) throws IOException, TransformerException, JAXBException {
		JAXBContext context =JAXBContext.newInstance(Catalog.class);
		String xsdFileName = "CatalogSchema.xsd";
		if(args.length != 0) {
			xsdFileName = args[0] + "/" + xsdFileName;
		}
		FileOutputStream s = new FileOutputStream(xsdFileName);
		pojoToXSD(context, s);
	}


	public static void pojoToXSD(JAXBContext context, OutputStream out)
		    throws IOException, TransformerException
		{
		    final List<DOMResult> results = new ArrayList<DOMResult>();

		    context.generateSchema(new SchemaOutputResolver() {
		        @Override
		        public Result createOutput(String ns, String file)
		                throws IOException {
		            DOMResult result = new DOMResult();
		            result.setSystemId(file);
		            results.add(result);
		            return result;
		        }
		    });

		    DOMResult domResult = results.get(0);
		    Document doc = (Document) domResult.getNode();

		    // Use a Transformer for output
		    TransformerFactory tFactory = TransformerFactory.newInstance();
		    Transformer transformer = tFactory.newTransformer();

		    DOMSource source = new DOMSource(doc);
		    StreamResult result = new StreamResult(out);
		    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		    transformer.transform(source, result);
		}

}
