// Synthetic fixture for BusinessDateJoinGuardTest's WIDENED pipeline-level red-proof (Phase 24
// D-07). Lives under src/test/resources, never src/main/java -- read as text and never compiled,
// so a killed or crashed run cannot leave a deliberately offending .java file inside a compiled
// source set.
// Exactly two interesting lines: one real offending calendar-date read on a chained Timeslot
// accessor that carries NO join verb at all (a plain local assignment, the shape N-1 hid behind),
// and one commented-out copy of it. The widened scan must therefore find exactly one entry, while
// the verb-scoped scan finds none -- proving the WIDENING, not the verb scan, is what catches it,
// and that comment-stripping still works as well as the walk and the match.
public class OffendingSample {

    void offend(AgentAssignment seat) {
        LocalDate calendarDate = seat.getTimeslot().getDate();
        // LocalDate calendarDate = seat.getTimeslot().getDate(); is deliberately commented out to prove comment-stripping
    }
}
