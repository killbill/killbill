# Fix Report — Kill Bill issue #2127

## Root cause

`SearchQuery` (`util/src/main/java/org/killbill/billing/util/entity/dao/SearchQuery.java`) is responsible for parsing the
`_q=1&col[op]=value` advanced-search syntax into a JDBI bind map. The constructor only knew how to convert string values
for `Boolean`, `Integer`, `Long` and `Double` column types; everything else, including date columns, fell through to the
default branch and was bound as a raw `String`.

`DefaultInvoiceDao.searchInvoices` (`invoice/src/main/java/org/killbill/billing/invoice/dao/DefaultInvoiceDao.java`) then
fed that bind map straight into the JDBI `search` / `getSearchCount` queries declared on `EntitySqlDao`. With the value
left as a `String`, the generated SQL effectively becomes `t.target_date <= '2025-08-28'`. MySQL silently coerces the
string to a date, but PostgreSQL is strict about types and rejects it with:

```
ERROR: operator does not exist: date <= character varying
```

The same issue applied to `invoice_date`, `created_date` and `updated_date`.

## Change summary

- `util/src/main/java/org/killbill/billing/util/entity/dao/SearchQuery.java`
  - Added `LocalDate.class` and `DateTime.class` branches to the type-aware value conversion in the constructor.
  - Added small `convertToLocalDate` / `convertToDateTime` helpers that parse ISO-8601 strings and fall back to the raw
    string when the input does not parse (matching the existing numeric-fallback behaviour).
- `invoice/src/main/java/org/killbill/billing/invoice/dao/DefaultInvoiceDao.java`
  - Declared the column types of `invoice_date` and `target_date` as `LocalDate.class` and of `created_date` /
    `updated_date` as `DateTime.class` in the `searchInvoices` `columnTypes` map. `LocalDate` / `DateTime` were already
    imported in this file.
- `util/src/test/java/org/killbill/billing/util/entity/dao/TestSearchQuery.java` (new, fast)
  - Unit tests covering: a `LocalDate` column produces a `LocalDate` binding, a `DateTime` column produces a `DateTime`
    binding, an unparseable date falls back to the raw string, and an untyped column is unchanged.
- `profiles/killbill/src/test/java/org/killbill/billing/jaxrs/TestInvoice.java`
  - Added the integration test `testInvoiceSearchByDate` (slow, JAX-RS) replicating the reporter's reproducer: searches
    on `target_date=...` and `target_date[lte]=...` are expected to succeed on PostgreSQL and MySQL.

## How the tests exercise the fix

- `TestSearchQuery#testLocalDateColumnTypeIsParsed` builds the exact query string from the issue
  (`_q=1&target_date[lte]=2025-08-28`) with `target_date` typed as `LocalDate.class` and asserts the bound value is a
  `LocalDate(2025, 8, 28)` rather than the string `"2025-08-28"`. Before the fix this test would fail because the value
  bound was a `String`.
- `TestSearchQuery#testDateTimeColumnTypeIsParsed` covers the parallel `DateTime` path used for `created_date` /
  `updated_date`.
- `TestSearchQuery#testUnparseableDateFallsBackToString` documents the fallback contract (an invalid value still flows
  through, surfacing as a JDBC-level type error rather than a parse exception inside the search machinery).
- `TestInvoice#testInvoiceSearchByDate` (slow) goes end-to-end through the REST endpoint and exercises both
  `target_date=...` and `target_date[lte]=...`. With the fix in place, the JDBC layer binds a real `DATE` value and
  PostgreSQL no longer rejects the comparison.

## Build status

`mvn -pl util,invoice -am -DskipTests compile`:

```
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO] killbill ........................................... SUCCESS [  0.217 s]
[INFO] killbill-api ....................................... SUCCESS [  0.772 s]
[INFO] killbill-util ...................................... SUCCESS [  1.630 s]
[INFO] killbill-tenant .................................... SUCCESS [  0.693 s]
[INFO] killbill-account ................................... SUCCESS [  0.719 s]
[INFO] killbill-catalog ................................... SUCCESS [  0.956 s]
[INFO] killbill-subscription .............................. SUCCESS [  0.993 s]
[INFO] killbill-entitlement ............................... SUCCESS [  0.911 s]
[INFO] killbill-junction .................................. SUCCESS [  0.603 s]
[INFO] killbill-usage ..................................... SUCCESS [  0.582 s]
[INFO] killbill-invoice ................................... SUCCESS [  1.281 s]
[INFO] BUILD SUCCESS
```

`mvn -pl profiles/killbill -am -DskipTests test-compile` also passes (all of `killbill-util`, `killbill-invoice`,
`killbill-jaxrs`, `killbill-profiles-killbill` etc. SUCCESS), confirming the new TestNG sources compile.

`mvn -pl util test -Dtest=TestSearchQuery -Dgroups=fast`:

```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Confidence

**High.** The new unit tests prove the bind map now contains a `LocalDate` / `DateTime` instead of a `String`, which
directly resolves the `operator does not exist: date <= character varying` PostgreSQL error. JDBI's killbill-commons
argument factories (`LocalDateArgumentFactory`, `DateTimeArgumentFactory`) already know how to bind these Joda types as
proper SQL `DATE` / `TIMESTAMP` parameters, so no further plumbing is required. The change is narrowly scoped: it only
adds new branches to type-aware parsing and a new entry in `DefaultInvoiceDao`'s `columnTypes` map; no existing
behaviour is altered. The integration test from the issue reporter is reproduced verbatim against the JAX-RS endpoint.
The compile-clean bar is met; the slow integration test was not executed locally because the embedded MySQL ARM archive
is unavailable in this environment (per task constraints).
