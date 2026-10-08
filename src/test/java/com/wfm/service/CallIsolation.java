package com.wfm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural proof that a singleton service holds no state across calls (Phase 24 D7). Each
 * desk supplier builds a FRESH fixture, runs it through the SAME service instance and returns a
 * value snapshot of the outcome, so two snapshots are equal exactly when the runs agree.
 *
 * <p>The first call of each desk, on a service nothing else has touched, is the isolated
 * baseline. The desks are then run alternately in succession and finally all at once from
 * several threads released together; every run must reproduce its desk's baseline. A map or
 * field that survived a call, or was shared between concurrent calls, would show up as a
 * snapshot that differs.
 */
final class CallIsolation {

    private static final int SUCCESSIVE_ROUNDS = 5;
    private static final int THREADS = 8;
    private static final int RUNS_PER_THREAD = 10;

    private CallIsolation() {
    }

    static <T> void assertNoStateSurvivesACall(Supplier<T> deskA, Supplier<T> deskB) throws Exception {
        T baselineA = deskA.get();
        T baselineB = deskB.get();
        assertThat(baselineA)
                .as("the two desks must have different outcomes, or a leak between them is invisible")
                .isNotEqualTo(baselineB);

        for (int round = 0; round < SUCCESSIVE_ROUNDS; round++) {
            assertThat(deskB.get()).as("desk B, successive round %d", round).isEqualTo(baselineB);
            assertThat(deskA.get()).as("desk A, successive round %d", round).isEqualTo(baselineA);
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> runs = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                boolean startWithA = t % 2 == 0;
                runs.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < RUNS_PER_THREAD; i++) {
                        boolean a = (i % 2 == 0) == startWithA;
                        assertThat(a ? deskA.get() : deskB.get())
                                .as("desk %s, concurrent run", a ? "A" : "B")
                                .isEqualTo(a ? baselineA : baselineB);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> run : runs) {
                run.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
