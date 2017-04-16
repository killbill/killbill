package org.killbill.billing.debug;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.xml.XmlConfiguration;

/**
 * Run a Jetty Server which does essentially the same as the "run" part of "mvn jetty:run", but as
 * a simple Java application inside Eclipse.
 * <p>
 * This allows debugging of a web application inside Eclipse as a regular Java application without
 * having to resort to m2eclipse or attaching to an externally running application container to debug and
 * get hot code replacement. It should be debugged as a Java application in the context of the Eclipse project,
 * created by mvn eclipse:eclipse.
 * <p>
 * To use, invoke @{code org.killbill.billing.debug.DebugJettyRun} with the following positional parameters:
 * <p><ul>
 * <li>Filesystem path to the Jetty configuration file (default {@code src/main/jetty-config/jetty-conf.xml}).
 * <li>Filesystem path to the web application directory, which contains {@code WEB-INF/web.xml} (default {@code src/main/webapp}).
 * 
 * @author gerhard
 *
 */
public class DebugJettyRun {

	private final Server server;
	
	public DebugJettyRun(String jettyXmlPath, String webAppPath) throws Exception {

		System.out.println("jettyXmlPath = " + jettyXmlPath);
		System.out.println("webAppPath = " + webAppPath);
		
		server = new Server();
        final XmlConfiguration jettyConf = new XmlConfiguration(Resource.toURL(FileUtils.getFile(jettyXmlPath)));
        jettyConf.configure(server);
        
        WebAppContext webApp = new WebAppContext();
        webApp.setTempDirectory(FileUtils.getFile("target/tmp"));
        webApp.setResourceBase(webAppPath);
        ContextHandlerCollection contexts = (ContextHandlerCollection)server.getChildHandlerByClass(ContextHandlerCollection.class);
        contexts.addHandler(webApp);        
	}
	
	public void start() throws Exception {
		server.start();
	}
	
	
	public static void main(String[] args) throws Exception {
		String jettyXmlPath = args.length > 0 ? args[0] : "src/main/jetty-config/jetty-conf.xml";
		String webAppPath = args.length > 1 ? args[1] : "src/main/webapp";
		DebugJettyRun server = new DebugJettyRun(jettyXmlPath, webAppPath);
		server.start();
	}
	
	
}
