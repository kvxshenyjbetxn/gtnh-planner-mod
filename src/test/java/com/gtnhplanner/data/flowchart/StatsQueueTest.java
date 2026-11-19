package com.gtnhplanner.data.flowchart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Which plans the Library works the stats out of: only those that lack them, once each. */
class StatsQueueTest {

    /** A graph with a drawer, so it is not empty. */
    private static Graph plan(final String name) {
        final Graph g = new Graph(name);
        g.addDrawer(new Drawer(Drawer.Kind.SOURCE, "item:x"));
        return g;
    }

    @Test
    void skipsPlansWithStatsAndEmptyOnes() {
        final Graph has = plan("has");
        has.setStats(new PlanStats(1, 0, 1, List.of(), List.of()));
        final Graph empty = new Graph("empty");
        final Graph wanted = plan("wanted");
        final StatsQueue q = new StatsQueue();
        assertEquals(List.of(wanted), q.waiting(List.of(has, empty, wanted)));
        assertSame(wanted, q.next(List.of(has, empty, wanted)));
    }

    @Test
    void skipsTaskBoardsAndUnreadablePlans() {
        final Graph tasks = plan("tasks");
        tasks.setPrimaryView(Tasks.TASKS);
        final Graph broken = plan("broken");
        broken.setUnreadable("x");
        assertNull(new StatsQueue().next(List.of(tasks, broken)));
    }

    @Test
    void aTriedPlanIsNotOfferedAgain() {
        final Graph a = plan("a"), b = plan("b");
        final StatsQueue q = new StatsQueue();
        assertSame(a, q.next(List.of(a, b)));
        q.tried(a);
        assertSame(b, q.next(List.of(a, b)));
        q.tried(b);
        assertTrue(
            q.waiting(List.of(a, b))
                .isEmpty());
    }
}
