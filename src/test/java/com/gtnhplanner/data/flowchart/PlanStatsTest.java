package com.gtnhplanner.data.flowchart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The My plans stats: worked out from the totals, kept in the save, sorted and filtered as the website does. */
class PlanStatsTest {

    private static PlanStats stats(final double eu, final int tier, final int machines, final String... makes) {
        return new PlanStats(eu, tier, machines, List.of(makes), List.of());
    }

    private static Plan plan(final PlanStats... all) throws ReflectiveOperationException {
        final java.lang.reflect.Constructor<Plan> constructor = Plan.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Plan plan = constructor.newInstance();
        int n = 0;
        for (final PlanStats s : all) {
            final Graph g = new Graph("P" + n++);
            g.setStats(s);
            plan.getGraphs()
                .add(g);
        }
        return plan;
    }

    @Test
    void worksTheStatsOutOfTheTotals() {
        final PlanStats s = PlanStats.of(
            120,
            3,
            new double[] { 0.2, 2.0, 0 },
            Map.of("item:gold", 4.0, "item:dust", 0.0),
            Map.of("fluid:water", 1.0));
        assertEquals(3, s.machines());
        assertEquals(3, s.tierIndex());
        assertEquals(List.of("item:gold"), s.makes());
        assertEquals(List.of("fluid:water"), s.takes());
    }

    @Test
    void roundTripsThroughJson() {
        final PlanStats s = new PlanStats(12.5, 4, 7, List.of("a", "b"), List.of("c"));
        assertEquals(s, PlanStats.fromJson(s.toJson()));
        assertNull(PlanStats.fromJson(null));
    }

    @Test
    void sortsAsTheWebsiteDoes() throws ReflectiveOperationException {
        final Plan p = plan(stats(100, 2, 5), null, stats(500, 4, 9), stats(10, 1, 20));
        assertEquals(
            List.of(2, 0, 3, 1),
            p.ordered(Plan.Sort.TIER)
                .subList(0, 4));
        assertEquals(List.of(2, 0, 3, 1), p.ordered(Plan.Sort.POWER));
        assertEquals(List.of(3, 0, 2, 1), p.ordered(Plan.Sort.LOW_POWER));
        assertEquals(List.of(3, 2, 0, 1), p.ordered(Plan.Sort.MACHINES));
    }

    @Test
    void filtersAsTheWebsiteDoes() {
        final PlanStats s = new PlanStats(100, 2, 5, List.of("gold"), List.of("water"));
        final Set<String> none = Set.of();
        assertTrue(PlanStats.passes(null, -1, -1, none, none));
        assertFalse(PlanStats.passes(null, 3, -1, none, none));
        assertTrue(PlanStats.passes(s, 2, 100, none, none));
        assertFalse(PlanStats.passes(s, 1, -1, none, none));
        assertFalse(PlanStats.passes(s, -1, 99, none, none));
        assertTrue(PlanStats.passes(s, -1, -1, Set.of("gold"), Set.of("water")));
        assertFalse(PlanStats.passes(s, -1, -1, Set.of("gold", "iron"), none));
    }

    @Test
    void savesWithTheSlot() {
        final Plan p = Plan.cloud("x");
        final Graph g = new Graph("Saved");
        final PlanStats s = new PlanStats(8, 1, 2, List.of("a"), List.of());
        g.setStats(s);
        p.getGraphs()
            .add(g);
        final String text = Serializer.planTree(p)
            .toString();
        assertTrue(text.contains("\"stats\""));
    }
}
