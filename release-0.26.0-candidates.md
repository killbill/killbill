# Kill Bill 0.26.0 — Release Candidate Issues

> **This is a candidate list for review.** Items below have been triaged as potential inclusions in the 0.26.0 release. Each section requires team sign-off before items are confirmed for the milestone. Tags indicate scope, breaking-change risk, and priority. Items flagged with inline notes need additional investigation before a decision can be made.

---

## Tag Legend

| Tag | Meaning |
|-----|---------|
| `API_CHANGE` | Introduces a breaking or additive change to a public API |
| `NEED_DESIGN` | Requires design discussion before implementation can begin |
| `AVIATE` | Directly supports the Aviate integration |
| `NICE_TO_HAVE` | Lower priority; include if bandwidth allows |
| `FINIX` | Raised by or directly relevant to the Finix integration |

---

## Features

| Description | Issue | PR | Tags |
|-------------|-------|----|------|
| Ability to control invoice `#id` | [#2014](https://github.com/killbill/killbill/issues/2014) | — | `NEED_DESIGN` `AVIATE` |
| Control invoice behavior to only generate invoices on BCD | [#1004](https://github.com/killbill/killbill/issues/1004) | — | `NEED_DESIGN` `AVIATE` `FINIX` |
| Limitations with process notifications and non-signed webhooks | [#2227](https://github.com/killbill/killbill/issues/2227) | — | `API_CHANGE` |
| Create subscription after successful payment | [#2002](https://github.com/killbill/killbill/issues/2002) | — | — |

---

## Aviate Support

> All items in this section are tagged `NICE_TO_HAVE`.

| Description | Issue | PR | Tags |
|-------------|-------|----|------|
| Materialize processing time for bus/notifications | [commons#171](https://github.com/killbill/killbill-commons/issues/171) | — | `API_CHANGE` `NICE_TO_HAVE` |
| Enhance usage plugin API to pass all transitions | [#2257](https://github.com/killbill/killbill/issues/2257) | — | `API_CHANGE` `NICE_TO_HAVE` |
| Enhance catalog plugin API to fetch shallow catalog | [#2258](https://github.com/killbill/killbill/issues/2258) | — | `API_CHANGE` `NICE_TO_HAVE` |

---

## Bugs

| Description | Issue | PR | Tags | Notes |
|-------------|-------|----|------|-------|
| Semantics for `effectiveDateForSubscription` does not match the doc | [#2079](https://github.com/killbill/killbill/issues/2079) | — | `FINIX` | |
| Catalog initialization from plugin leads to lots of contention | [#2116](https://github.com/killbill/killbill/issues/2116) | — | `FINIX` | Verify whether this has already been resolved |
| `BCD_UPDATE` event not returned from subscription events | [#2224](https://github.com/killbill/killbill/issues/2224) | [#2254](https://github.com/killbill/killbill/pull/2254) | `API_CHANGE` | |
| Missing expiry event type | [#1921](https://github.com/killbill/killbill/issues/1921) | — | `API_CHANGE` | |
| Add API to list all available role definitions | [#2250](https://github.com/killbill/killbill/issues/2250) | — | `API_CHANGE` | |
| Create new `ExtBusEventType` corresponding to `UNDO_CHANGE` | [#1841](https://github.com/killbill/killbill/issues/1841) | — | `API_CHANGE` | |

---

## Security

> **Review needed:** Confirm which items warrant inclusion in 0.26.0 and assess severity before committing to the milestone.

| Description | Issue | PR | Tags |
|-------------|-------|----|------|
| `api_secret` and `api_salt` stored in plaintext in tenants table | [#2248](https://github.com/killbill/killbill/issues/2248) | — | — |
| `AdminResource` endpoints lack permission checks, exposing queue data and server health controls to any authenticated user | [#2251](https://github.com/killbill/killbill/issues/2251) | — | — |
| Prevent CORS origin reflection with credentials | — | [#2182](https://github.com/killbill/killbill/pull/2182) | — |
| Validate push notification callback URLs to prevent SSRF | — | [#2183](https://github.com/killbill/killbill/pull/2183) | — |

---

## Pending PRs

> PRs already open and awaiting review/merge.

| Description | Issue | PR | Tags |
|-------------|-------|----|------|
| Creating an ADD_ON associated with a pending BASE subscription fails | [#1355](https://github.com/killbill/killbill/issues/1355) | [#1932](https://github.com/killbill/killbill/pull/1932) | — |
| Incorrect invoice item dates for `CBA_ADJ` item | [#2202](https://github.com/killbill/killbill/issues/2202) | [#2226](https://github.com/killbill/killbill/pull/2226) | — |
