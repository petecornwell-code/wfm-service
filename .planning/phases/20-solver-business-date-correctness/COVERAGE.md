# API Coverage — Phase 20: Solver Business-Date Correctness

No external API integration: this phase re-keys internal date resolution across the project's own
Spring controllers, services and Timefold constraint provider — no third-party service, SDK or
remote endpoint is called, and no dependency is added.

The deterministic detector returned `detected: true` on a single signal — the token pair
`(surface) api` inside a javadoc sentence about **Timefold 1.16.0's public Constraint Streams API**,
a compile-time library already in `build.gradle`, not a service this phase integrates with. Re-read
of the phase scope confirms the negative: the four verbs the detector scans
(`integrate`/`wrap`/`connect`/`consume`/`wire`) appear only in descriptions of wiring one internal
Java method to another. Fabricating a capability matrix for a surface that does not exist would be
the opt-out-without-a-reason failure this gate exists to close, inverted.

The three gap-closure plans in this run (`20-09`, `20-10`, `20-11`) touch one REST endpoint —
`POST /api/v1/desks/{deskId}/timeslots/generate` — but it is this application's **own** endpoint,
already live and already exposed by `frontend/src/api/client.ts`. No capability is added to it, no
request or response field changes, and its tenant boundary is covered by `T-20-09-01` in
`20-09-PLAN.md`'s threat model rather than by a coverage matrix.
