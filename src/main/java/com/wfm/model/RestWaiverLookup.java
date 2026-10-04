package com.wfm.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The single is-this-pair-waived predicate in this codebase (REST-06, D-08) — the same
 * static-predicate-with-delegating-overloads register as {@link ShiftBandPair#covers}, applied
 * to {@link AgentRestWaiver}.
 *
 * <p><b>D-06's direction, stated in full.</b> {@code businessDateEntered} is the business date the
 * agent is ENTERING — the successor side of the pair, the day the agent starts early. A waiver
 * recorded for that date waives the gap coming INTO it, from the previous business date's shift
 * end. It does NOT waive the rest after that date's shift; clearing that pair needs its own waiver
 * recorded on the following date. The parameter is named for the direction precisely so a call
 * site cannot pass the predecessor date by accident and have it read naturally — a call written as
 * {@code waives(waiver, prev.agentId(), prev.businessDate())} reads, correctly, as asking whether
 * the wrong date was waived.
 *
 * <p><b>This is the only implementation, by design.</b> Three consumers must agree about what
 * "waived" means: the SHIFT constraint ({@code ScheduleConstraintProvider.minimumRestShift}), the
 * SLOT constraint ({@code ScheduleConstraintProvider.minimumRestSlot}), and REST-03's pre-solve
 * refusal (plan 22-07). Any future change to what "waived" means must be made HERE, once, so those
 * three can never disagree. {@code RestWaiverPredicateGuardTest} is the enforcement mechanism and
 * {@code src/test/resources/rest-waiver-predicate-guard.md} is its registry — the registry is the
 * authority on the exact set of expected call sites.
 */
public final class RestWaiverLookup {

    private RestWaiverLookup() {
    }

    /**
     * THE predicate. True when {@code waiver} is non-null, its agent is non-null, that agent's id
     * equals {@code agentId}, and its date equals {@code businessDateEntered}. Null-safe on every
     * argument and returns false rather than throwing — this runs inside a {@code filtering}
     * joiner during score calculation, where a thrown exception aborts the solve rather than
     * surfacing as a usable error (T-22-12). False is also the safe default: an unmatched waiver
     * leaves the hard-adjacent rest constraint enforcing, never silently disabled.
     */
    public static boolean waives(AgentRestWaiver waiver, UUID agentId, LocalDate businessDateEntered) {
        if (waiver == null || agentId == null || businessDateEntered == null) {
            return false;
        }
        Agent agent = waiver.getAgent();
        if (agent == null || agent.getId() == null) {
            return false;
        }
        return agent.getId().equals(agentId) && businessDateEntered.equals(waiver.getDate());
    }

    /**
     * Convenience entry point for a caller holding the whole problem-fact collection — this is the
     * entry point plan 22-07's pre-solve refusal calls. Delegates to {@link #waives} inside a
     * stream {@code anyMatch} and performs no comparison of its own, so that caller is never the
     * second implementation the guard forbids. A null or empty list is false.
     */
    public static boolean isWaived(List<AgentRestWaiver> waivers, UUID agentId, LocalDate businessDateEntered) {
        if (waivers == null || waivers.isEmpty()) {
            return false;
        }
        return waivers.stream().anyMatch(w -> waives(w, agentId, businessDateEntered));
    }
}
