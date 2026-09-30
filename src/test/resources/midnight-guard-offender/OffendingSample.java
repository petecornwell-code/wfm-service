// Synthetic fixture for MidnightTimeArithmeticGuardTest's pipeline-level red-proof (BDAY-05).
// Lives under src/test/resources, never src/main/java -- copied as a resource and never
// compiled, so a killed or crashed run cannot leave a deliberately offending file inside a
// compiled source set. Exactly two interesting lines: one real offending comparison on a
// scheduling-time-named receiver, and one commented-out line naming the same token. The
// pipeline must therefore find exactly one entry, proving comment-strip works as well as the
// walk and the match -- a two-entry result would mean comment stripping silently stopped
// working.
public class OffendingSample {

    void offend(java.time.LocalTime slotStart, java.time.LocalTime cutoff) {
        if (slotStart.isBefore(cutoff)) {
            // reachable
        }
        // slotStart.isBefore(cutoff) is deliberately commented out to prove comment-stripping
    }
}
