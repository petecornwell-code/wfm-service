# Phase 13 — API Coverage Declaration

No external API integration: this phase migrates internal read paths (`DeskAgentService.toResponse`,
`DeskAgentExportService`) from the retired `Agent.contractedHoursPerDay` scalar to the authoritative
`agent_day_hours` model and adds one internal REST endpoint
(`PUT /api/v1/desks/{deskId}/agents/{agentId}/day-hours/{day}`) against this application's own
controller layer.

**Why the detector may fire and why it is a false positive here.** BambooHR appears in the phase
scope in exactly two roles, neither of which is an integration this phase builds:

1. As an explicitly **out-of-scope deferred item** — audit finding I-2, the manual "Refresh from
   BambooHR" button bypassing `AgentMergeService`, recorded under `## Deferred Ideas` in
   `13-CONTEXT.md` and under "Out of scope" in the ROADMAP Phase 13 section.
2. As one of the five **pre-existing writers** of the retired scalar
   (`BambooRefreshService.java:244`), cited only as a constraint — the read path must not assume the
   scalar is null. No line of `BambooRefreshService` is modified by any plan in this phase.

`13-RESEARCH.md` states directly: "No new libraries are introduced by this phase… No new external
tool, service, or runtime dependency is introduced," and its Package Legitimacy Audit records
"Not applicable. This phase installs zero new external packages in any ecosystem (npm, Maven)."

No capability matrix is produced because there is no external capability surface to subtract from.
