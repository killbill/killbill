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
package com.ning.billing.jaxrs;

import static org.testng.Assert.assertNotNull;


import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;

import java.util.UUID;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.eclipse.jetty.servlet.FilterHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;




import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.SubscriptionJson;
import com.ning.billing.server.listeners.KillbillGuiceListener;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.ning.jetty.core.CoreConfig;
import com.ning.jetty.core.server.HttpServer;

public class TestAccount {

	private static final Logger log = LoggerFactory.getLogger(TestAccount.class);

	public static final String HEADER_CONTENT_TYPE = "Content-type";
	public static final String CONTENT_TYPE = "application/json";

	private ObjectMapper mapper;

	private HttpServer server;
	private AsyncHttpClient httpClient;
	private CoreConfig config;

	CoreConfig getConfig() {
		return new CoreConfig() {

			@Override
			public boolean isSSLEnabled() {
				return false;
			}
			@Override
			public boolean isJettyStatsOn() {
				return false;
			}
			@Override
			public int getServerSslPort() {
				return 0;
			}
			@Override
			public int getServerPort() {
				return 8080;
			}
			@Override
			public String getServerHost() {
				return "127.0.0.1";
			}
			@Override
			public String getSSLkeystorePassword() {
				return null;
			}
			@Override
			public String getSSLkeystoreLocation() {
				return null;
			}
			@Override
			public int getMinThreads() {
				return 2;
			}
			@Override
			public int getMaxThreads() {
				return 100;
			}
			@Override
			public String getLogPath() {
				return "/var/tmp/.logs";
			}
		};
	}

	  public static void loadSystemPropertiesFromClasspath(final String resource) {
	        final URL url = TestAccount.class.getResource(resource);
	        assertNotNull(url);

	        try {
	            System.getProperties().load( url.openStream() );
	        } catch (IOException e) {
	            throw new RuntimeException(e);
	        }
	    }


	@BeforeClass(groups="slow")
	public void setup() throws Exception {

		loadSystemPropertiesFromClasspath("/killbill.properties");

		httpClient = new AsyncHttpClient();
		server = new HttpServer();
		config = getConfig();
		mapper = new ObjectMapper();
		final Iterable<EventListener> eventListeners = new Iterable<EventListener>() {
			@Override
			public Iterator<EventListener> iterator() {
				ArrayList<EventListener> array = new ArrayList<EventListener>();
				array.add(new KillbillGuiceListener());
				return array.iterator();
			}
		};
		server.configure(config, eventListeners, new HashMap<FilterHolder, String>());
		server.start();
	}


	AccountJson getAccountJson() {
		String accountId = UUID.randomUUID().toString();
		String name = "yoyo bozo2";
		int length = 4;
		String externalKey = "xdfsdretuq";
		String email = "yoyo@gmail.com";
		int billCycleDay = 12;
		String currency = "USD";
		String paymentProvider = "paypal";
		String timeZone = "UTC";
		String address1 = "12 rue des ecoles";
		String address2 = "Poitier";
		String company = "Renault";
		String state = "Poitou";
		String country = "France";
		String phone = "81 53 26 56";

		AccountJson accountJson = new AccountJson(accountId, name, length, externalKey, email, billCycleDay, currency, paymentProvider, timeZone, address1, address2, company, state, country, phone);
		return accountJson;
	}

	@Test(groups="slow")
	public void testFoo() throws Exception {
		
		ObjectMapper mapper = new ObjectMapper();
		
		AccountJson accountData = getAccountJson();

		  ObjectWriter objWriter = mapper.writer();

          Writer writer = new StringWriter();
          objWriter.writeValue(writer, accountData);
          String baseJson = writer.toString();

          log.info(baseJson);

          AccountJson objFromJson = mapper.readValue(baseJson, AccountJson.class);

          log.info(objFromJson.toString());
	}
	
	
	@Test(groups="slow")
	public void testAccountOk() throws Exception {

		final String accountPathPrefix = "/1.0/account";

		AccountJson accountData = getAccountJson();
		ObjectWriter objWriter = mapper.writer();

        Writer writer = new StringWriter();
        objWriter.writeValue(writer, accountData);
        String baseJson = writer.toString();
        
        try {
        	Thread.sleep(100000);
        } catch (Exception e) {}
        
		httpClient.preparePost(String.format("http://%s:%d%s", config.getServerHost(), config.getServerPort(), accountPathPrefix))
		.addHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE)
		.setBody(baseJson)
		.execute(new AsyncCompletionHandler<Integer>() {

			@Override
			public Integer onCompleted(Response response)
			throws Exception {

				int statusCode = response.getStatusCode();
				URI uri = response.getUri();
				return statusCode;
			}
		});
	}
}
