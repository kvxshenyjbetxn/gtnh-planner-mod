package com.gtnhplanner.data.flowchart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Which plans still need their {@link PlanStats} worked out for the Library, and which were tried already this game
 * (a plan whose solve failed is not tried in a loop). Pure: the driver ({@code ui/library/StatsBackfill}) does the
 * solving.
 */
public final class StatsQueue {

    private final Set<Graph> tried = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Whether {@code g} is worth working out: no stats yet, not tried, readable, not a task board, not empty. */
    public boolean wants(final Graph g) {
        return g.getStats() == null && !tried.contains(g)
            && g.getUnreadable() == null
            && !Tasks.TASKS.equals(g.getPrimaryView())
            && !(g.getNodes()
                .isEmpty()
                && g.getDrawers()
                    .isEmpty());
    }

    /** The plans of {@code graphs} still wanted, in order. */
    public List<Graph> waiting(final List<Graph> graphs) {
        final List<Graph> out = new ArrayList<>();
        for (final Graph g : graphs) if (wants(g)) out.add(g);
        return out;
    }

    /** The next plan to work out, or null when none is wanted. */
    @Nullable
    public Graph next(final List<Graph> graphs) {
        for (final Graph g : graphs) if (wants(g)) return g;
        return null;
    }

    /** Marks {@code g} tried: it is not offered again this game, whatever came of it. */
    public void tried(final Graph g) {
        tried.add(g);
    }
}
