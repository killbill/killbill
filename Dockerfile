FROM maven:3.8.4-openjdk-11 AS build

# Clone your forked repository
ARG KILLBILL_REPO=https://github.com/your-organization/killbill.git
ARG KILLBILL_BRANCH=master

WORKDIR /app
RUN git clone --depth 1 --branch ${KILLBILL_BRANCH} ${KILLBILL_REPO} .

# Build Kill Bill
RUN mvn clean install -DskipTests

# Create the runtime container
FROM tomcat:9.0-jdk11-openjdk

# Remove default Tomcat apps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the built WAR file
COPY --from=build /app/profiles/killbill/target/killbill-profiles-killbill*.war /usr/local/tomcat/webapps/ROOT.war

# Set environment variables
ENV KILLBILL_DAO_URL=jdbc:mysql://db:3306/killbill
ENV KILLBILL_DAO_USER=killbill
ENV KILLBILL_DAO_PASSWORD=killbill
ENV KILLBILL_SERVER_TEST_MODE=true

# Create directory for config files
RUN mkdir -p /var/lib/killbill

EXPOSE 8080

CMD ["catalina.sh", "run"]