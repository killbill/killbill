#!/bin/bash

# Navigate to the project root directory
cd /home/site/wwwroot

# Run the Maven command
 mvn -Dorg.killbill.server.properties=file:///PROJECT_ROOT/profiles/killbill/src/main/resources/killbill-server.properties -Dlogback.configurationFile=./profiles/killbill/src/main/resources/logback.xml jetty:run
