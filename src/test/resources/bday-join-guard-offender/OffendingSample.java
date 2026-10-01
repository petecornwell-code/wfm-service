// Synthetic fixture for BusinessDateJoinGuardTest's pipeline-level red-proof (SOLV-02). Lives
// under src/test/resources, never src/main/java -- read as text and never compiled, so a killed
// or crashed run cannot leave a deliberately offending .java file inside a compiled source set.
// Exactly two interesting lines: one real offending join-key occurrence on a bare `ts` Timeslot
// receiver, and one commented-out line naming the same shape. The pipeline must therefore find
// exactly one entry, proving comment-strip works as well as the walk and the match -- a two-entry
// result would mean comment stripping silently stopped working.
public class OffendingSample {

    void offend(java.util.Map<java.time.LocalDate, java.util.List<Object>> timeslotsByDate,
            Timeslot ts) {
        timeslotsByDate.computeIfAbsent(ts.getDate(), k -> new java.util.ArrayList<>());
        // timeslotsByDate.computeIfAbsent(ts.getDate(), k -> new java.util.ArrayList<>()) is deliberately commented out to prove comment-stripping
    }
}
