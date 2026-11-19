package com.gtnhplanner.ui.library;

import java.util.List;

import javax.annotation.Nullable;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.api.PlanAPI;
import com.gtnhplanner.data.flowchart.Graph;
import com.gtnhplanner.data.flowchart.Plan;
import com.gtnhplanner.data.flowchart.PlanStats;
import com.gtnhplanner.data.flowchart.StatsQueue;
import com.gtnhplanner.data.flowchart.balancer.BalanceMode;
import com.gtnhplanner.data.flowchart.balancer.SolveInput;
import com.gtnhplanner.data.flowchart.balancer.SolveService;
import com.gtnhplanner.ui.BoardSession;

/**
 * Works out the stats of every plan that has none, one at a time, while the Library shows My plans: the sorts and
 * filters by tier, power and machines need them, and a plan gets them only by being solved. Each plan is snapshotted
 * on the client thread ({@link SolveInput#of}), solved on a solver thread of its own, and its totals built back on
 * the client thread by {@link BoardSession#statsFor} (the same numbers as an opened plan). The save is written once
 * when the queue drains. Driven by {@link #tick}, which the Library calls every client tick; {@link #stop} (the
 * Library closing) stops the solver and the work resumes on the next {@code tick}.
 */
final class StatsBackfill {

    /** Tried plans last the whole game, so a plan that will not solve is not asked again. */
    private static final StatsQueue QUEUE = new StatsQueue();

    @Nullable
    private SolveService solver;
    @Nullable
    private Graph job;
    @Nullable
    private SolveInput jobInput;
    private int done;
    private boolean changed;

    /** Moves the work on a step. Client thread only. */
    void tick() {
        if (Plan.sandboxed()) return;
        final List<Graph> graphs = Plan.getInstance()
            .getGraphs();
        if (job == null) {
            final Graph next = QUEUE.next(graphs);
            if (next == null) {
                finish();
                return;
            }
            try {
                jobInput = SolveInput.of(next, BalanceMode.AUTO, null);
            } catch (final RuntimeException e) {
                fail(next, e);
                return;
            }
            if (solver == null) solver = new SolveService();
            job = next;
            solver.submit(0, jobInput);
            return;
        }
        final SolveService.Result latest = solver.latest();
        if (latest == null || latest.input() != jobInput) return;
        final Graph graph = job;
        job = null;
        jobInput = null;
        QUEUE.tried(graph);
        if (latest.error() != null || latest.balance() == null) {
            GtnhPlanner.LOG.info("Library: no stats for plan '{}', its solve failed", graph.getName());
            return;
        }
        try {
            final PlanStats stats = BoardSession.statsFor(graph, latest.balance());
            if (stats != null) {
                graph.setStats(stats);
                done++;
                changed = true;
            }
        } catch (final RuntimeException e) {
            fail(graph, e);
        }
    }

    private void fail(final Graph graph, final RuntimeException e) {
        QUEUE.tried(graph);
        job = null;
        jobInput = null;
        GtnhPlanner.LOG.info("Library: no stats for plan '{}': {}", graph.getName(), e.toString());
    }

    /** The queue is empty: save what was worked out, once, and let the solver go. */
    private void finish() {
        stop();
        if (changed && !Plan.getInstance()
            .isCloud()) PlanAPI.save();
        changed = false;
        done = 0;
    }

    /** Stops the solver (the Library closing); the plan being worked on is asked for again next time. */
    void stop() {
        if (solver != null) solver.close();
        solver = null;
        job = null;
        jobInput = null;
        if (changed && !Plan.getInstance()
            .isCloud()) PlanAPI.save();
        changed = false;
    }

    /** Whether plans are still being worked out. */
    boolean working() {
        return job != null || QUEUE.next(
            Plan.getInstance()
                .getGraphs())
            != null;
    }

    /** "Working out plan stats (3 of 12)", or null when nothing is. */
    @Nullable
    String line() {
        if (!working()) return null;
        final int left = QUEUE.waiting(
            Plan.getInstance()
                .getGraphs())
            .size();
        return "Working out plan stats (" + (done + 1) + " of " + (done + left) + ")";
    }
}
