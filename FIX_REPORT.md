# Fix Report — Issue #2157

## Root cause / design summary

Issue #2157 asks to adopt PostgreSQL as the default database engine of the Kill
Bill reference implementation. Per Pierre's comment, no single class hard-codes
a "default" engine at runtime — but the **operator-facing default** is the JDBC
URL that ships in `profiles/killbill/src/main/resources/killbill-server.properties`
(the file that `./bin/start-server` consumes by default via the
`org.killbill.server.properties` system property). That file was set to
`jdbc:mysql://127.0.0.1:3306/killbill` for both the core DAO
(`org.killbill.dao.url`) and the OSGi plugin DAO
(`org.killbill.billing.osgi.dao.url`). The companion helper script
`bin/db-helper` also defaulted `DRIVER="mysql"`. Together, these are the
user-visible "default" — anyone following the README path lands on MySQL.

## Change summary

- **`profiles/killbill/src/main/resources/killbill-server.properties`** — switched
  `org.killbill.dao.url` and `org.killbill.billing.osgi.dao.url` from
  `jdbc:mysql://127.0.0.1:3306/killbill` to
  `jdbc:postgresql://127.0.0.1:5432/killbill`, and updated the default user /
  password to `postgres` / `postgres` (the conventional defaults for a fresh
  PostgreSQL install; the canonical `root` / `root` defaults are MySQL-specific).
  Added a short comment documenting the new default and that operators can
  override these properties to switch engines.

- **`bin/db-helper`** — changed `DRIVER="mysql"` to `DRIVER="postgres"`,
  switched the default `USER` / `PWD` to `postgres` / `postgres`, and updated
  the `usage()` text accordingly. The script already supported both engines via
  the `--driver` flag; only the default changed.

- **`profiles/killbill/src/test/java/org/killbill/billing/server/dao/TestDefaultDatabaseConfig.java`**
  (new) — TestNG regression test in the `fast` group that loads the shipped
  `killbill-server.properties` from the classpath and asserts (a) both DAO URLs
  start with `jdbc:postgresql:` and (b) `EmbeddedDBFactory.get(daoConfig)`
  resolves to `EmbeddedDB.DBEngine.POSTGRESQL`. The latter exercises the same
  parsing path used at runtime by `KillBillEmbeddedDBProvider`.

## How the test exercises the fix / new behaviour

`TestDefaultDatabaseConfig#testDefaultDaoUrlIsPostgresql` loads the properties
file straight from the classpath (the file under
`profiles/killbill/src/main/resources/`) and asserts both `org.killbill.dao.url`
and `org.killbill.billing.osgi.dao.url` start with `jdbc:postgresql:`. This
guards against accidental regressions to MySQL/H2 in the default properties.

`TestDefaultDatabaseConfig#testEmbeddedDBFactoryResolvesToPostgresql` builds a
`DaoConfig` (using the same `AugmentedConfigurationObjectFactory` machinery as
the existing `TestEmbeddedDBFactory`) from the shipped properties and runs the
config through `EmbeddedDBFactory.get(...)`. It asserts the resolved
`DBEngine` is `POSTGRESQL`. This mirrors the exact code path that
`KillBillEmbeddedDBProvider` (and ultimately `GlobalLockerModule`,
`DBTestingHelper`, etc.) take at runtime to decide engine-specific behaviour.

## Build status

`mvn -pl profiles/killbill -am -DskipTests ... compile`:

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO] killbill ........................................... SUCCESS
[INFO] killbill-api ....................................... SUCCESS
[INFO] killbill-util ...................................... SUCCESS
[INFO] killbill-tenant .................................... SUCCESS
[INFO] killbill-account ................................... SUCCESS
[INFO] killbill-catalog ................................... SUCCESS
[INFO] killbill-currency .................................. SUCCESS
[INFO] killbill-subscription .............................. SUCCESS
[INFO] killbill-entitlement ............................... SUCCESS
[INFO] killbill-junction .................................. SUCCESS
[INFO] killbill-usage ..................................... SUCCESS
[INFO] killbill-invoice ................................... SUCCESS
[INFO] killbill-overdue ................................... SUCCESS
[INFO] killbill-payment ................................... SUCCESS
[INFO] killbill-beatrix ................................... SUCCESS
[INFO] killbill-jaxrs ..................................... SUCCESS
[INFO] killbill-profiles .................................. SUCCESS
[INFO] killbill-profiles-killbill ......................... SUCCESS
[INFO] BUILD SUCCESS
```

`mvn -pl profiles/killbill -am -DskipTests ... test-compile` also reports
`BUILD SUCCESS`, and the new test class lands at
`profiles/killbill/target/test-classes/org/killbill/billing/server/dao/TestDefaultDatabaseConfig.class`.

(Compile invocation passes the standard `-Dcheck.skip-*=true` flags used by the
project's own CI workflow because the cached snapshot artifacts in the local
Maven repository miss some test-classifier jars unrelated to this change.)

## Confidence level

**Medium-high.** The change is small, targeted, and limited to the operator-
facing default configuration plus a regression test. The existing PostgreSQL
support in Kill Bill is mature (see `GlobalLockerModule`, `ddl-postgresql.sql`,
`bin/db-helper`'s existing `postgres` branch, `wipeoutTenant-postgresql.sql`,
`trimTenant-postgresql.sql`, the CI workflow's PostgreSQL setup steps), so
flipping the default does not break any code path — it just changes which
engine an operator running `./bin/start-server` (with no overrides) connects
to.

Lower-confidence aspects:
- The e2e GitHub Actions matrix in `.github/workflows/ci.yml` still pins
  `database: 'mysql'`. That is a CI configuration choice (matrix-driven) and
  intentionally left alone — the issue asks about the project default, not
  about which engine CI exercises. Operators or maintainers can flip the
  matrix entry separately if desired.
- I could not run the new test end-to-end on this machine because the test
  classpath cannot start an embedded PostgreSQL on Apple Silicon (same family
  of issue as the MySQL ARM tar.gz failure noted in the task brief).
  Compile-clean is the bar for build verification per the task constraints,
  and the test reaches `BUILD SUCCESS` in `test-compile`.
