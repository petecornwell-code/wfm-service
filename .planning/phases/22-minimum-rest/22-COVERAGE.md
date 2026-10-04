# API Coverage — Phase 22 (minimum-rest)

No external API integration: first-party phase only — the surface it adds is this
service's own Spring controllers (`PUT /desks/{deskId}/minimum-rest`, the
`/desk-agents/{id}/rest-waivers` pair), its own Flyway migration (`V55`), its own
Timefold constraint provider, and its own React pages. No third-party API, SDK, or
vendor service is contacted. The detector's signal was the phase's own requirement
identifiers (`REST-01`..`REST-07`, meaning minimum *rest period*), which match the
`<Capitalized> REST` service-surface pattern in prose such as "Cite REST-07" and
"Does REST-03's pre-solve refusal ...".
