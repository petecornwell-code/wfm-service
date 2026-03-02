package com.wfm.service;

import org.springframework.stereotype.Service;

/**
 * Implements the Erlang X (Extended Erlang C) calculation.
 * Uses the Jagerman formula for Erlang C probability to avoid factorial overflow,
 * then extends with abandonment and retrial modelling.
 * See spec section 4.4 for algorithm details.
 */
@Service
public class ErlangXService {

    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_AGENTS = 10_000;

    /**
     * Calculate the required number of agents for a single timeslot/specialization.
     *
     * @param callVolume            forecasted calls for this timeslot
     * @param aht                   average handle time in seconds
     * @param patience              average caller patience in seconds
     * @param retryRate             percentage of abandoned callers who retry (0-100)
     * @param serviceLevelTarget    target percentage of calls answered within threshold (0-100)
     * @param serviceLevelThreshold max acceptable wait time in seconds
     * @return the minimum number of agents needed
     */
    public int calculateRequiredAgents(int callVolume, double aht, double patience,
                                       double retryRate, double serviceLevelTarget,
                                       int serviceLevelThreshold) {
        if (callVolume <= 0 || aht <= 0) {
            return 0;
        }

        double retryFraction = retryRate / 100.0;
        double slTarget = serviceLevelTarget / 100.0;

        // Iterative Erlang X: adjust offered load by retrials until convergence
        double adjustedCallVolume = callVolume;

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double trafficIntensity = adjustedCallVolume * aht / 3600.0; // in Erlangs (per hour)

            int agents = findMinAgents(trafficIntensity, aht, patience, slTarget, serviceLevelThreshold);

            // Calculate abandonment with this staffing level
            double erlangCProb = erlangCProbability(agents, trafficIntensity);
            double avgWait = erlangCProb * aht / (agents - trafficIntensity);
            if (avgWait < 0) avgWait = 0;

            // Probability of abandonment (exponential patience model)
            double pAbandon = 0;
            if (patience > 0 && avgWait > 0) {
                pAbandon = erlangCProb * (1.0 - Math.exp(-(agents - trafficIntensity) / (trafficIntensity * patience / aht)));
            }

            double abandonedCalls = adjustedCallVolume * pAbandon;
            double retrials = abandonedCalls * retryFraction;
            double newAdjustedVolume = callVolume + retrials;

            // Convergence check: change < 1 agent difference in load
            if (Math.abs(newAdjustedVolume - adjustedCallVolume) < 1.0) {
                return agents;
            }
            adjustedCallVolume = newAdjustedVolume;
        }

        // Final pass with converged load
        double trafficIntensity = adjustedCallVolume * aht / 3600.0;
        return findMinAgents(trafficIntensity, aht, patience, slTarget, serviceLevelThreshold);
    }

    /**
     * Find the smallest number of agents that meets the service level target.
     */
    private int findMinAgents(double trafficIntensity, double aht,
                               double patience, double slTarget, int slThreshold) {
        int minAgents = Math.max(1, (int) Math.ceil(trafficIntensity));

        for (int n = minAgents; n <= MAX_AGENTS; n++) {
            double sl = serviceLevel(n, trafficIntensity, aht, patience, slThreshold);
            if (sl >= slTarget) {
                return n;
            }
        }
        return MAX_AGENTS;
    }

    /**
     * Calculate the service level (fraction of calls answered within threshold)
     * using the Erlang X model (Erlang C with abandonment).
     */
    private double serviceLevel(int agents, double trafficIntensity,
                                 double aht, double patience, int slThreshold) {
        if (agents <= trafficIntensity) {
            return 0.0;
        }

        double erlangC = erlangCProbability(agents, trafficIntensity);

        // Probability of waiting > slThreshold (Erlang C: P(W > t) = C(n,A) * e^(-(n-A)*t/AHT))
        double exponent = -(agents - trafficIntensity) * slThreshold / aht;
        double pWaitExceeds = erlangC * Math.exp(exponent);

        // With abandonment: callers leave the queue exponentially with rate 1/patience
        // Adjusted service level accounts for callers who abandon before slThreshold
        if (patience > 0) {
            double abandonRate = 1.0 / patience;
            double mu = (agents - trafficIntensity) / aht; // service excess rate
            // Fraction who abandon before being answered within threshold
            double pAbandonBeforeThreshold = erlangC *
                    (abandonRate / (abandonRate + mu)) *
                    (1.0 - Math.exp(-(abandonRate + mu) * slThreshold));

            return Math.min(1.0, 1.0 - pWaitExceeds + pAbandonBeforeThreshold);
        }

        return 1.0 - pWaitExceeds;
    }

    /**
     * Erlang C probability using the Jagerman recursive formula.
     * Computes P(wait > 0) = probability that a call has to wait.
     * Uses iterative computation to avoid factorial overflow.
     */
    private double erlangCProbability(int agents, double trafficIntensity) {
        if (agents <= 0 || trafficIntensity <= 0) {
            return 0.0;
        }
        if (agents <= trafficIntensity) {
            return 1.0; // System is overloaded
        }

        // Jagerman formula: compute iteratively
        // B(0, A) = 1
        // B(i, A) = (A * B(i-1, A)) / (i + A * B(i-1, A))   (Erlang B recursive)
        // C(N, A) = B(N, A) / (1 - rho + rho * B(N, A))      where rho = A/N
        double erlangB = 1.0;
        for (int i = 1; i <= agents; i++) {
            erlangB = (trafficIntensity * erlangB) / (i + trafficIntensity * erlangB);
        }

        double rho = trafficIntensity / agents;
        return erlangB / (1.0 - rho + rho * erlangB);
    }
}
