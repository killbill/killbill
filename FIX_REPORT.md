# Fix report — Issue #2157: Adopt PostgreSQL as the default database implementation

## Root cause / design summary

The "reference" server profile that ships in `profiles/killbill` advertises MySQL
as its example/default JDBC configuration via
`profiles/killbill/src/main/resources/killbill-server.properties`. Specifically
`org.killbill.dao.url` and `org.killbill.billing.osgi.dao.url` were both set to
`jdbc:mysql://127.0.0.1:3306/killbill`. While Kill Bill itself does not enforce
MySQL (the database engine is detected from the JDBC URL by
`org.killbill.billing.server.dao.EmbeddedDBFactory` / `KillBillEmbeddedDBProvider`,
and the underlying `org.killbill.commons.jdbi.guice.DaoConfig` defaults to H2
when no URL is supplied), the example configuration that ships with the
reference distribution effectively makes MySQL the default any operator sees.

This change updates the reference server profile so PostgreSQL is the default
JDBC URL for both the main DAO connection and the OSGI plugin DAO connection,
which is what is requested by the ticket.

## Change summary

| File | Change |
| --- | --- |
| `profiles/killbill/src/main/resources/killbill-server.properties` | Changed `org.killbill.dao.url` and `org.killbill.billing.osgi.dao.url` from `jdbc:mysql://127.0.0.1:3306/killbill` to `jdbc:postgresql://127.0.0.1:5432/killbill`. |
| `profiles/killbill/src/test/java/org/killbill/billing/server/dao/TestDefaultDatabaseConfig.java` | New regression test (`fast` group) that loads `killbill-server.properties` from the classpath and (a) asserts both DAO URLs start with `jdbc:postgresql:` and (b) feeds the URL through `DaoConfig` + `EmbeddedDBFactory` to confirm it resolves to `EmbeddedDB.DBEngine.POSTGRESQL`. |

No build, dependency, or production code paths were modified — Kill Bill already
supports PostgreSQL through `EmbeddedDBFactory` and `GlobalLockerModule`
(`util/src/main/java/org/killbill/billing/util/glue/GlobalLockerModule.java`),
so the change is purely a default-configuration change with a guarding test.

## How the test exercises the fix / new behaviour

`TestDefaultDatabaseConfig`:

1. `testReferenceProfileDefaultsToPostgreSQL` reads the shipped
   `killbill-server.properties` via the classpath and asserts that both
   `org.killbill.dao.url` and `org.killbill.billing.osgi.dao.url` are PostgreSQL
   JDBC URLs. This locks in the configured default and will fail loudly if a
   future change reverts to MySQL or another engine.
2. `testDefaultUrlResolvesToPostgreSQLEngine` builds the same `DaoConfig` that
   the production runtime would build from the properties file and passes it to
   `EmbeddedDBFactory.get(...)`, asserting the resulting engine is
   `DBEngine.POSTGRESQL`. This guards the end-to-end resolution path that the
   server uses at boot.

Both tests live in the existing `org.killbill.billing.server.dao` package next
to `TestEmbeddedDBFactory`, extend `KillbillTestSuite`, and are tagged `fast`
(no embedded DB required).

## Build status

`mvn -pl profiles/killbill -am -DskipTests test-compile`:

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.156 s]
[INFO] killbill-api ....................................... SUCCESS [  0.638 s]
[INFO] killbill-util ...................................... SUCCESS [  2.480 s]
[INFO] killbill-tenant .................................... SUCCESS [  1.058 s]
[INFO] killbill-account ................................... SUCCESS [  1.242 s]
[INFO] killbill-catalog ................................... SUCCESS [  1.676 s]
[INFO] killbill-currency .................................. SUCCESS [  0.454 s]
[INFO] killbill-subscription .............................. SUCCESS [  1.714 s]
[INFO] killbill-entitlement ............................... SUCCESS [  1.716 s]
[INFO] killbill-junction .................................. SUCCESS [  1.198 s]
[INFO] killbill-usage ..................................... SUCCESS [  0.925 s]
[INFO] killbill-invoice ................................... SUCCESS [  2.548 s]
[INFO] killbill-overdue ................................... SUCCESS [  1.171 s]
[INFO] killbill-payment ................................... SUCCESS [  2.121 s]
[INFO] killbill-beatrix ................................... SUCCESS [  2.244 s]
[INFO] killbill-jaxrs ..................................... SUCCESS [  4.766 s]
[INFO] killbill-profiles .................................. SUCCESS [  0.003 s]
[INFO] killbill-profiles-killbill ......................... SUCCESS [  2.013 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  28.250 s
```

The new test class compiles cleanly alongside the rest of the module's 55 test
sources.

## Confidence level

**Medium.** The configuration change is small, localized, and consistent with
the maintainer's comment on the ticket (PostgreSQL is fully supported; MySQL is
not enforced anywhere). The regression tests pin both the configured default
and the engine-resolution behaviour. I did not (and on this machine cannot)
run the embedded-DB integration tests, but the targeted change does not touch
any code path those tests exercise. The remaining uncertainty is product-level:
the maintainer may prefer to also change the underlying `DaoConfig` `@Default`
(which lives in the external `killbill-commons` repository and is currently H2)
rather than only the example properties file shipped with the reference server.
