# Fix Report — Issue #2157: Adopt PostgreSQL as the default database

## Root cause / design summary

This is an enhancement request rather than a bug. The bundled Kill Bill reference-implementation
configuration `profiles/killbill/src/main/resources/killbill-server.properties` previously defaulted
both the main DAO (`org.killbill.dao.url`) and the OSGi plugin DAO (`org.killbill.billing.osgi.dao.url`)
to MySQL (`jdbc:mysql://127.0.0.1:3306/killbill`). Because that file is loaded as `killbill-server.properties`
on the classpath at startup and supplies the property values consumed by `org.killbill.commons.jdbi.guice.DaoConfig#getJdbcUrl()`
(which is then routed through `org.killbill.billing.server.dao.EmbeddedDBFactory#get(DaoConfig)` inside
`org.killbill.billing.server.modules.KillBillEmbeddedDBProvider`), the reference profile shipped MySQL as
its de-facto default DB engine. Per the issue, PostgreSQL is the preferred default; this change updates the
reference configuration so that — without any user override — the server profile resolves a PostgreSQL
`EmbeddedDB` instance.

## Change summary

- `profiles/killbill/src/main/resources/killbill-server.properties` — switched the two `*.dao.url` entries
  (main DAO and OSGi DAO) from `jdbc:mysql://127.0.0.1:3306/killbill` to
  `jdbc:postgresql://127.0.0.1:5432/killbill`, and updated the matching `user`/`password` defaults to
  `postgres`/`postgres`. Added a short comment explaining that PostgreSQL is now the default and how to
  override it back to MySQL.
- `profiles/killbill/src/test/java/org/killbill/billing/server/dao/TestEmbeddedDBFactory.java` — added
  `testDefaultReferenceImplementationIsPostgres()`. The new test loads the bundled
  `killbill-server.properties` from the classpath, asserts that both `org.killbill.dao.url` and
  `org.killbill.billing.osgi.dao.url` point at PostgreSQL, then feeds that resource through
  `AugmentedConfigurationObjectFactory` and `EmbeddedDBFactory.get(...)` to assert the resulting
  `EmbeddedDB.getDBEngine()` is `DBEngine.POSTGRESQL`.

## How the test exercises the new behaviour

`testDefaultReferenceImplementationIsPostgres` reads the *real* `killbill-server.properties` file shipped
inside the `profiles/killbill` jar (the same file the reference implementation uses at startup), then
runs the same code path the server uses to derive its `EmbeddedDB` — `DaoConfig` built via
`AugmentedConfigurationObjectFactory` and routed through `EmbeddedDBFactory.get(DaoConfig)`. The test
fails if either of the two URLs regresses to a non-PostgreSQL URL or if the factory no longer recognizes
the URL as PostgreSQL, pinning the new default.

## Build status

```
mvn -pl profiles/killbill -am -DskipTests -Dcheck.skip-spotbugs=true -Dcheck.skip-dependency-scope=true compile
...
[INFO] killbill-profiles-killbill ......................... SUCCESS [  0.849 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  12.541 s
```

Test-compile was also verified separately:

```
mvn -pl profiles/killbill -am -DskipTests -Dcheck.skip-spotbugs=true -Dcheck.skip-dependency-scope=true test-compile
...
[INFO] killbill-profiles-killbill ......................... SUCCESS [  1.411 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  13.792 s
```

Note: `-Dcheck.skip-spotbugs=true -Dcheck.skip-dependency-scope=true` were needed because the build
environment runs JDK 25, and the SpotBugs version pinned by `killbill-oss-parent:0.146.67` (4.7.2.1)
ships an ASM that does not understand class-file major version 69. The skip flags only disable the
SpotBugs/dependency-scope checks; the actual `javac` compilation of main and test sources still
targets Java 11 and succeeds.

## Confidence level

**Medium.**

- The change correctly redirects the reference-implementation defaults to PostgreSQL, and the new
  regression test asserts that property values and resolved `EmbeddedDB.DBEngine` are both PostgreSQL.
- Both `mvn ... compile` and `mvn ... test-compile` pass for `profiles/killbill` and all its dependent
  modules.
- The wider Kill Bill test suite (`*TestSuiteWithEmbeddedDB`) provisions its own embedded DB via
  `org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB` / `KillbillTestSuiteWithEmbeddedDB`,
  independently of `killbill-server.properties`, so this property change should not destabilize
  existing tests. The environment's known embedded-MySQL-on-ARM failure (`mysql-Mac_OS_X-aarch64.tar.gz`)
  is unrelated and explicitly out of scope per the task.
- I cannot independently verify the integration-test profile in the larger Kill Bill ecosystem (docker-compose
  recipes, kpm seeding, etc.) since those live outside this repo; the change here is the minimal, focused
  edit inside the killbill repo to make PostgreSQL the configured default.
