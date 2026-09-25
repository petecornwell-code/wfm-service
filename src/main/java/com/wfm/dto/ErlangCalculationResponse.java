package com.wfm.dto;

/**
 * One staffing answer, with the diagnostics needed to judge it and the adjustment arithmetic shown
 * rather than folded in. Nothing here is persisted: this is the response of a calculation, not the
 * representation of a stored staffing requirement.
 *
 * <p><b>Which headcount each diagnostic describes.</b> {@code serviceLevel}, {@code
 * probabilityOfWait}, {@code probabilityOfAbandon}, {@code occupancy} and {@code
 * averageSpeedOfAnswerSeconds} are all at {@code agentsRequired} — the queueing answer. If an
 * occupancy ceiling raised the count, {@code occupancyAtHandlingAgents} is the occupancy actually
 * worked, and every other diagnostic is then conservative: more agents than the target needs can
 * only improve them.
 *
 * <p><b>Loads are after concurrency.</b> Dividing by concurrency is a division of the load, so a
 * chat desk at 3 concurrent contacts reports a third of the voice load for the same volume.
 *
 * @param model                      {@code ERLANG_C} or {@code ERLANG_X} — which maths answered
 * @param agentsRequired             smallest headcount meeting the target, before any adjustment
 * @param handlingAgents             after the occupancy ceiling; agents that must be on contacts
 * @param scheduledAgents            after shrinkage — the number to roster, the operator's answer
 * @param occupancyRelief            agents added purely to respect the occupancy ceiling
 * @param shrinkageUplift            agents added purely to cover shrinkage
 * @param offeredLoad                Erlangs the model solved, including converged retrials
 * @param baseOfferedLoad            Erlangs from first-attempt contacts only; equals
 *                                   {@code offeredLoad} for Erlang C, which has no retrials
 * @param serviceLevel               fraction answered within the threshold at {@code agentsRequired}
 * @param probabilityOfWait          P(a caller waits at all) at {@code agentsRequired}
 * @param occupancy                  contact-handling fraction at {@code agentsRequired}
 * @param occupancyAtHandlingAgents  the same, at {@code handlingAgents} — what the ceiling bought
 * @param probabilityOfAbandon       null for Erlang C, which models no abandonment
 * @param averageSpeedOfAnswerSeconds null for Erlang X: its waiting-time distribution is phase-type
 *                                   and this class will not report a mean it cannot derive exactly
 * @param retrialIterations          null for Erlang C; for Erlang X, fixed-point passes used — a
 *                                   high value means a fragile input, not a better answer
 */
public record ErlangCalculationResponse(
        String model,
        int agentsRequired,
        int handlingAgents,
        int scheduledAgents,
        int occupancyRelief,
        int shrinkageUplift,
        double offeredLoad,
        double baseOfferedLoad,
        double serviceLevel,
        double probabilityOfWait,
        double occupancy,
        double occupancyAtHandlingAgents,
        Double probabilityOfAbandon,
        Double averageSpeedOfAnswerSeconds,
        Integer retrialIterations
) {}
