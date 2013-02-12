[![Build Status](https://travis-ci.org/killbill/killbill.png)](https://travis-ci.org/killbill/killbill)

Killbill is an open source subscription management/billing system.
You can find the documentation [here](http://ning.github.com/killbill/).

Setting up your own tenant
--------------------------

Killbill supports multiple tenants running on the same server. Each tenant needs to identify itself when using the /1.0
API via HTTP Basic authentication.

For example, trying to access all tag definitions without being authenticated would throw a 400 error:

    ~> curl -v http://127.0.0.1:8080/1.0/kb/tagDefinitions
    * About to connect() to 127.0.0.1 port 8080 (#0)
    *   Trying 127.0.0.1... connected
    * Connected to 127.0.0.1 (127.0.0.1) port 8080 (#0)
    > GET /1.0/kb/tagDefinitions HTTP/1.1
    > User-Agent: curl/7.21.4 (universal-apple-darwin11.0) libcurl/7.21.4 OpenSSL/0.9.8r zlib/1.2.5
    > Host: 127.0.0.1:8080
    > Accept: */*
    >
    < HTTP/1.1 401 Unauthorized
    < WWW-Authenticate: BASIC realm="application"
    < Content-Length: 0
    < Server: Jetty(8.1.2.v20120308)
    <
    * Connection #0 to host 127.0.0.1 left intact
    * Closing connection #0


Before you can use the /1.0 API, you need to create your own tenant. To do so, post your username (`apiKey`) and password
(`apiSecret`) to the `/1.0/kb/tenants` endpoint (the header `X-Killbill-CreatedBy` is used for auditing purposes).
For example, to create the a tenant with the credentials bob/lazar:

    ~> curl -v -XPOST \
               -H'Content-Type: application/json' \
               -H'X-Killbill-CreatedBy: admin' \
               -d'{"apiKey": "bob", "apiSecret": "lazar"}' \
               http://127.0.0.1:8080/1.0/kb/tenants
    * About to connect() to 127.0.0.1 port 8080 (#0)
    *   Trying 127.0.0.1... connected
    * Connected to 127.0.0.1 (127.0.0.1) port 8080 (#0)
    > POST /1.0/kb/tenants HTTP/1.1
    > User-Agent: curl/7.21.4 (universal-apple-darwin11.0) libcurl/7.21.4 OpenSSL/0.9.8r zlib/1.2.5
    > Host: 127.0.0.1:8080
    > Accept: */*
    > Content-Type: application/json
    > X-Killbill-CreatedBy: admin
    > Content-Length: 39
    >
    < HTTP/1.1 201 Created
    < Location: http://127.0.0.1:8080/1.0/kb/tenants/f07bc7d5-00e8-48bd-8b43-ef8537219171
    < Content-Type: application/json
    < Transfer-Encoding: chunked
    < Server: Jetty(8.1.2.v20120308)
    <
    * Connection #0 to host 127.0.0.1 left intact
    * Closing connection #0
    {"uri":"/1.0/kb/tenants/f07bc7d5-00e8-48bd-8b43-ef8537219171"}


You can now access the API using basic auth, e.g.:

    ~> curl -v http://127.0.0.1:8080/1.0/kb/tagDefinitions -ubob:lazar
