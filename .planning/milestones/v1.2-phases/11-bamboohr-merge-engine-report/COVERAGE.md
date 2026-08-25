# API Coverage — BambooHR

> Full coverage by default. Opt-outs are explicit, reasoned decisions.
>
> Phase 11 (BambooHR Merge Engine & Report) adds **no new BambooHR endpoint** — it reuses the
> existing `BambooHRClient` surface (`listEmployees`, `listTimeOff`) and calls it at a new time
> (before every upload, tenant-wide, per MRG-01/D-04) rather than reaching for new capabilities.
> This matrix is nonetheless the full subtraction record for the integration, so later phases start
> from recorded decisions rather than zero.

## Integrated surface

The `BambooHRClient` interface (`src/main/java/com/wfm/integration/BambooHRClient.java`) exposes
three operations, backed by `HttpBambooHRClient` (prod) and `MockBambooHRClient` (dev default via
`bamboohr.mock: true`).

| Endpoint | Client method | Capability rows below |
|---|---|---|
| `POST /reports/custom?format=JSON` | `listEmployees` | employee directory, working-days pattern, employment status, job title / department / email |
| `GET /time_off/requests` | `listTimeOff` | approved time-off |
| `GET /employees/{id}?fields=…` | `getEmployee` | single-employee lookup |

| capability | decision | reason |
|---|---|---|
| employee directory (custom report) | INTEGRATE | |
| working-days pattern (field 4517) | INTEGRATE | |
| approved time-off requests | INTEGRATE | |
| single-employee lookup | INTEGRATE | |
| employment status filtering | INTEGRATE | |
| job title, department, work email | INTEGRATE | |

## Deliberate opt-outs

| capability | decision | reason |
|---|---|---|
| time-off create / approve / reject (write) | OPT-OUT | WFM is strictly read-only against BambooHR; D-03 forbids the upload path from writing back to BambooHR at all |
| employee create / update (write) | OPT-OUT | BambooHR is the authoritative source in the merge precedence rule (MRG-02); writing back would invert the phase's whole premise |
| webhooks / change subscriptions | OPT-OUT | D-04 locks a synchronous fresh sync on every upload, which makes push-based cache invalidation unnecessary; explicitly out of scope |
| time tracking / timesheets | OPT-OUT | not needed — scheduling consumes the working-days pattern and PTO only, never clocked hours |
| benefits, compensation, payroll | OPT-OUT | out of scope and deliberately not fetched — sensitive PII with no scheduling use; narrowing the custom report's field list is itself the mitigation for T-11-02 |
| training / performance / goals | OPT-OUT | not needed — no scheduling input |
| employee photos and files | OPT-OUT | not needed — no UI surface consumes them |
| company reports other than the one custom report | OPT-OUT | the single custom report already carries every field the merge engine reads; a second report would be redundant fetch cost against MRG-01's per-upload budget (T-11-05) |
| metadata / field-discovery endpoints | OPT-OUT | field `4517` is pinned explicitly in the report request; runtime discovery would add a call per upload for no behavioural gain |
| tabular data (dependents, emergency contacts, etc.) | OPT-OUT | not needed — no scheduling input, and same PII-minimization reasoning as benefits |

## Notes carried into this phase

- **No new endpoint, new call frequency.** Phase 11's change is *when* and *how broadly* the existing
  surface is called: a whole-tenant `listEmployees` before every upload instead of only on desk
  refresh. That cost is accepted as **T-11-05** (Denial of Service, medium, `accept`) — D-04 locks
  synchronous fresh sync and explicitly rejects sync reuse.
- **Timeout now explicit.** `HttpBambooHRClient.java:48` used `RestClient.create()` with no request
  factory and therefore **no timeout at all**. Plan 11-01 Task 3 installs
  `bamboohr.http.connect-timeout-seconds` (10) and `bamboohr.http.read-timeout-seconds` (120). See
  RESEARCH Open Question 3 (RESOLVED).
- **Failure is total, never partial.** MRG-07 requires a fetch failure to abort the whole upload with
  zero writes; `BambooHRRateLimitedException` and any other fetch-time exception propagate uncaught
  out of the pre-transaction fetch step (T-11-01, high, `mitigate`).
- **Pre-existing, out-of-scope security item.** STATE.md Backlog 999.7 records that the BambooHR API
  key exposed 2026-06-02 was never rotated. Phase 11 does not touch key storage or rotation and is
  not scoped to fix it, but this phase does increase key usage frequency. Flagged for follow-up, not
  mitigated here.
- **Concurrency is documented, not coordinated.** An upload-triggered sync does not acquire
  `BambooRefreshService`'s per-desk `refreshInProgress` guard, so a manual desk refresh and an upload
  can fetch concurrently. Pre-existing; carried, not fixed. See RESEARCH Open Question 2 (RESOLVED)
  and the resolved edge at `11-01-PLAN.md:52`.
