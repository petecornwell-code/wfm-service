package com.wfm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural proof that a singleton service holds no state across calls (Phase 24 D7). Each
 * desk supplier PREPARES a fresh fixture and returns the call to make: running it sends that
 * fixture through the SAME service instance and returns a value snapshot of the outcome, so two
 * snapshots are equal exactly when the runs agree.
 *
 * <p>The first call of each desk, on a service nothing else has touched, is the isolated
 * baseline. The desks are then run alternately in succession and finally concurrently from
 * several threads; every run must reproduce its desk's baseline. In the concurrent half every
 * fixture is built before the threads are released, and the threads meet at a barrier before
 * each call, so what overlaps is the service calls themselves rather than fixture construction.
 * A map or field that survived a call, or was shared between concurrent calls, would show up as
 * a snapshot that differs.
 */
final class CallIsolation {

    private static final int SUCCESSIVE_ROUNDS = 5;
    private static final int THREADS = Math.max(4, Runtime.getRuntime().availableProcessors());
    private static final int RUNS_PER_THREAD = 10;
    private static final long DEADLINE_SECONDS = 120;

    private CallIsolation() {
    }

    static <T> void assertNoStateSurvivesACall(Supplier<Callable<T>> deskA, Supplier<Callable<T>> deskB)
            throws Exception {
        T baselineA = deskA.get().call();
        T baselineB = deskB.get().call();
        assertThat(baselineA)
                .as("the two desks must have different outcomes, or a leak between them is invisible")
                .isNotEqualTo(baselineB);

        for (int round = 0; round < SUCCESSIVE_ROUNDS; round++) {
            assertThat(deskB.get().call()).as("desk B, successive round %d", round).isEqualTo(baselineB);
            assertThat(deskA.get().call()).as("desk A, successive round %d", round).isEqualTo(baselineA);
        }

        // Thread t's run i is desk A when (t + i) is even, so each round half the threads run
        // each desk at the same moment.
        List<List<Callable<T>>> prepared = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            List<Callable<T>> calls = new ArrayList<>();
            for (int i = 0; i < RUNS_PER_THREAD; i++) {
                calls.add((t + i) % 2 == 0 ? deskA.get() : deskB.get());
            }
            prepared.add(calls);
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CyclicBarrier inStep = new CyclicBarrier(THREADS);
            List<Future<?>> runs = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int thread = t;
                runs.add(pool.submit(() -> {
                    start.await();
                    try {
                        for (int i = 0; i < RUNS_PER_THREAD; i++) {
                            boolean a = (thread + i) % 2 == 0;
                            inStep.await(DEADLINE_SECONDS, TimeUnit.SECONDS);
                            assertThat(prepared.get(thread).get(i).call())
                                    .as("desk %s, concurrent run: thread %d, iteration %d", a ? "A" : "B", thread, i)
                                    .isEqualTo(a ? baselineA : baselineB);
                        }
                    } catch (Throwable failure) {
                        inStep.reset(); // release the other threads now rather than at the deadline
                        throw failure;
                    }
                    return null;
                }));
            }
            start.countDown();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
            Throwable firstOther = null;
            for (Future<?> run : runs) {
                try {
                    run.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                } catch (ExecutionException e) {
                    // One failed run breaks the barrier for the rest; the assertion is the real cause.
                    if (e.getCause() instanceof AssertionError assertion) {
                        throw assertion;
                    }
                    if (firstOther == null) {
                        firstOther = e.getCause();
                    }
                } catch (TimeoutException e) {
                    throw new AssertionError("concurrent runs did not finish within " + DEADLINE_SECONDS + " s", e);
                }
            }
            if (firstOther != null) {
                throw new AssertionError("a concurrent run failed without an assertion", firstOther);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
