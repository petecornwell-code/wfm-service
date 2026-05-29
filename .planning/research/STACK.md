# Technology Stack — v1.1 Additions

**Project:** WFM Service v1.1 Schedule Quality & Reporting
**Researched:** 2026-05-07
**Scope:** Incremental additions to existing Spring Boot 3.4.2 + Timefold Solver 1.16.0 + React 19 + Vite stack

---

## What Already Exists (Do Not Re-research)

| Layer | Current |
|-------|---------|
| Backend framework | Spring Boot 3.4.2, Java 21 |
| Solver | Timefold Solver 1.16.0 (BOM) |
| Excel I/O | Apache POI 5.3.0 (poi-ooxml) |
| Frontend | React 19, Vite 6, react-router-dom 7 |
| DB | PostgreSQL 16 via Spring Data JPA + Flyway |
| Infra | ECS Fargate, RDS, CloudFront/S3 |

---

## New Dependencies Needed for v1.1

### Frontend: Charting (Coverage Report + Agent Utilization Report)

**Add: `recharts` 3.8.x**

```bash
npm install recharts
```

- Version: 3.8.1 (latest stable as of March 2025; confirm via `npm install recharts@latest`)
- React 19 peer dependency declared as `^19.0.0` in recharts 3.x — compatible with this project's React 19
- Chart types needed: stacked `BarChart` (demand vs coverage per timeslot), `ComposedChart` (bar + line overlay), `AreaChart` (utilization trends)
- `ResponsiveContainer` wraps all charts for CloudFront SPA layout without fixed widths
- `stackId` prop on `Bar` components handles the demand/supply overlay directly

Why recharts over alternatives:
- Nivo has richer customization but 10x fewer weekly downloads and heavier canvas/SVG hybrid overhead not needed here
- Victory has strong accessibility but sparse chart types and lower community velocity
- Recharts is declarative React components over D3, composable, tree-shakeable; 50M+ weekly npm downloads; actively maintained

**Do NOT add** `chart.js` or `react-chartjs-2` — imperative API, harder to compose with React state, and recharts covers all required chart types.

---

### Backend: PDF Export (Schedule Export Improvements)

**Add: `openpdf` 3.0.4**

```gradle
implementation 'com.github.librepdf:openpdf:3.0.4'
```

- License: LGPL/MPL — no AGPL exposure, safe for internal commercial product without source disclosure
- iText 7/8 is AGPL — requires either buying a commercial license or open-sourcing the product; reject it
- OpenPDF is a maintained fork of iText 4, API is nearly identical, drop-in for basic PDF table generation
- Handles: page layout, tables, cell padding/borders, fonts, headers/footers — sufficient for schedule + utilization reports
- Released 2026-05-01 (3.0.4), active maintenance confirmed
- Artifact coordinates changed in 3.x: `com.github.librepdf:openpdf:3.0.4` (groupId unchanged, package renamed to `org.openpdf` internally)

Why not Apache FOP: FOP targets XSL-FO templating, overly complex for tabular schedule output.
Why not Jasper Reports: Too heavy, requires report design files, overkill for this use case.

---

### Backend: Apache POI Upgrade (Excel Export Improvements)

**Upgrade: `poi-ooxml` from 5.3.0 to 5.5.1**

```gradle
implementation 'org.apache.poi:poi-ooxml:5.5.1'
```

- Released 2025-11-30; fixes CVEs and upgrades BouncyCastle/other transitive deps
- Required for improved conditional formatting (color-scale highlighting of coverage gaps in exported Excel), named styles, and reliable cell formula evaluation
- `SheetConditionalFormatting` + `XSSFConditionalFormattingRule` enable gap-highlighting without manual cell color logic
- `XSSFColor` and `XSSFCellStyle` API stable across 5.x — no migration required
- The existing `FteSpreadsheetGenerator` utility and `preferences.xlsx` read path continue to work unchanged

---

### Backend: Timefold Solver Upgrade (Score Breakdown + Quality Tuning)

**Upgrade: Timefold BOM from 1.16.0 to 1.33.0**

```gradle
implementation platform('ai.timefold.solver:timefold-solver-bom:1.33.0')
```

**Critical licensing fact:**
- In Timefold Solver 1.x (all versions through 1.33): `ScoreAnalysis`, `SolutionManager.analyze()`, constraint justifications are **included in the free Apache 2.0 open-source edition**
- In Timefold Solver 2.0+: explainability features moved to the paid "Plus" edition
- **Do NOT upgrade to 2.0** — this project uses score explanation for the "solver score breakdown" feature; staying on 1.33 (last 1.x LTS) retains free access

1.33.0 is a pure bugfix/maintenance release over 1.16.0:
- Fixes null value handling in sortable value ranges (relevant to preference handling)
- Fixes list unassign move generation fairness (relevant to shift balance constraints)
- Fixes lock release in event consumption
- No API changes to constraint-defining code or Spring Boot auto-configuration

**ScoreAnalysis API available via existing `timefold-solver-spring-boot-starter`** — no additional dependency needed:

```java
@Autowired
SolutionManager<Schedule, HardSoftScore> solutionManager;

// In a REST endpoint:
ScoreAnalysis<HardSoftScore> analysis = solutionManager.analyze(schedule);
// analysis.constraintMap() → per-constraint score contributions
// analysis.summarize() → human-readable breakdown for logging
// Serializes to JSON automatically via Spring Boot's Jackson integration
```

For large schedules prefer `ScoreAnalysisFetchPolicy.FETCH_MATCH_COUNT` over default `FETCH_ALL` to avoid serializing every individual match.

---

### Backend: No New Dependencies Needed for These Features

**Solver fairness / balance constraints:** Implement as new Constraint Stream methods in the existing `ScheduleConstraintProvider`. No library addition. Pattern: `penalize()` with `HardSoftScore.ofSoft(...)` on imbalance between agents' assigned hours.

**Consistent hours constraints:** Same pattern — additional `@ConstraintConfiguration` fields on `ScheduleConstraintConfiguration` (if using that pattern) or direct constraint weights.

**Preference satisfaction tracking:** A computed field on the solution returned from the solver — count of satisfied vs total preferences — no additional library.

**PTO sync diagnostic:** Spring Data JPA query methods against existing `PtoRecord`/`Exception` entities. New REST endpoints on existing `@RestController`. No library addition.

**Agent desk bulk upload:** Apache POI already in project. Add new endpoint + service class reading the uploaded `.xlsx` using `WorkbookFactory.create(inputStream)`. No library addition.

---

### Frontend: No New Framework Needed

**Coverage/utilization report UI:** Built with recharts (above) + existing React Router routes. No state management library needed — data fetched per-page via `fetch()` from Spring Boot REST endpoints, stored in `useState`/`useEffect`. Project is too small to justify Redux or Zustand.

**Schedule export trigger:** A download button calling a Spring Boot `/export/excel` or `/export/pdf` endpoint via `window.open()` or `fetch` with blob response. No frontend library needed.

---

## Summary of Changes to Build Files

### `build.gradle` additions

```gradle
// Upgrade Timefold BOM (line 36):
implementation platform('ai.timefold.solver:timefold-solver-bom:1.33.0')

// Upgrade Apache POI (line 42):
implementation 'org.apache.poi:poi-ooxml:5.5.1'

// Add OpenPDF for PDF export:
implementation 'com.github.librepdf:openpdf:3.0.4'
```

### `frontend/package.json` addition

```json
"recharts": "^3.8.1"
```

---

## Alternatives Considered

| Category | Recommended | Rejected | Reason |
|----------|-------------|----------|--------|
| React charting | recharts 3.8 | nivo | Lower downloads, heavier; overkill |
| React charting | recharts 3.8 | chart.js / react-chartjs-2 | Imperative API, poor React composition |
| PDF generation | OpenPDF 3.0.4 | iText 7/8 | AGPL licensing forces source disclosure or paid license |
| PDF generation | OpenPDF 3.0.4 | Jasper Reports | Heavy, needs design files, not worth it |
| PDF generation | OpenPDF 3.0.4 | Apache FOP | XSL-FO templating, wrong tool for tables |
| Timefold version | 1.33.0 (1.x LTS) | 2.0 | ScoreAnalysis moves to paid Plus in 2.0 |
| Frontend state | None (useState) | Redux/Zustand | Unjustified complexity for per-page report data |

---

## Confidence

| Area | Confidence | Basis |
|------|------------|-------|
| recharts version + React 19 compat | HIGH | GitHub peerDependencies confirmed `^19.0.0`, issue #4558 closed, v3.8.1 release verified |
| OpenPDF version + license | HIGH | Official GitHub releases page, LGPL/MPL license confirmed |
| Apache POI 5.5.1 | HIGH | Official poi.apache.org download page |
| Timefold 1.33 ScoreAnalysis in open source | HIGH | Blog post + docs + community discussion cross-confirmed; 2.0 pays-wall announcement explicitly states 1.x remains free |
| Timefold 2.0 avoidance rationale | HIGH | Official 2.0 release blog explicitly names ScoreAnalysis as Plus feature |
| No additional frontend state library needed | MEDIUM | Based on project scope; could revisit if report state grows complex |

---

## Sources

- [Recharts npm page](https://www.npmjs.com/package/recharts)
- [Recharts React 19 peer dependency discussion](https://github.com/recharts/recharts/discussions/5701)
- [Recharts 3.0 migration guide](https://github.com/recharts/recharts/wiki/3.0-migration-guide)
- [OpenPDF GitHub releases](https://github.com/LibrePDF/OpenPDF/releases)
- [Apache POI download page](https://poi.apache.org/download.html)
- [Timefold Solver 1.4.0 explainable score announcement](https://timefold.ai/blog/timefold-solver-1-4-brings-explainable-score)
- [Timefold Solver 2.0 release (Plus edition announcement)](https://timefold.ai/blog/timefold-solver-2.0-release)
- [Timefold understanding-the-score docs](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/understanding-the-score)
- [iText AGPL licensing](https://itextpdf.com/en/products/itext-7/itext-7-core)
- [Best React chart libraries 2025 — LogRocket](https://blog.logrocket.com/best-react-chart-libraries-2025/)
