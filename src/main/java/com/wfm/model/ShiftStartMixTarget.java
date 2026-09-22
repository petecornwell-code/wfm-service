package com.wfm.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * (Phase 18, MIX-01) How many agent-days should start at {@code startTime} on {@code date} — a
 * pre-solve problem fact, not a planning variable.
 *
 * <p><b>Why the multiset is an input.</b> Coverage depends only on the multiset of (envelope,
 * break band) pairs worked on a date; WHO holds each one is a separate question, solved after the
 * fact by {@code ScheduleConsistencyRepairService}. The solver cannot choose that multiset well:
 * changing one agent-day's start means re-pointing its envelope AND every seat that fills it, and
 * {@code solverConfig.xml}'s {@code 0hard} annealing temperature refuses every intermediate state,
 * so whatever the construction heuristic picks stands for the whole solve. See
 * {@code ShiftStartMixTargetService} for what that cost on the live desk.
 *
 * <p>One row per (date, template start time) — deliberately NOT per (date, ShiftBandPair). Which
 * break band an agent takes is a genuine coverage trade-off the solver is good at and should keep
 * making freely; only the start-time mix is pinned.
 */
public record ShiftStartMixTarget(LocalDate date, LocalTime startTime, int targetCount) {}
