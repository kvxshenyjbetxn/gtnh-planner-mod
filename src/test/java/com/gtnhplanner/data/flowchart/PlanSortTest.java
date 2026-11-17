package com.gtnhplanner.data.flowchart;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The Library's orders for My plans: last opened, last edited, name and newest, with their fallbacks. */
class PlanSortTest {

    private static Plan plan(final String... names) throws ReflectiveOperationException {
        final java.lang.reflect.Constructor<Plan> constructor = Plan.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Plan plan = constructor.newInstance();
        for (final String name : names) plan.getGraphs()
            .add(new Graph(name));
        return plan;
    }

    private static Graph at(final Plan plan, final int i) {
        return plan.getGraphs()
            .get(i);
    }

    @Test
    void lastOpenedPutsTheActivePlanFirst() throws ReflectiveOperationException {
        final Plan plan = plan("A", "B", "C");
        at(plan, 0).setLastOpen(30);
        at(plan, 1).setLastOpen(10);
        at(plan, 2).setLastOpen(20);
        plan.setActiveIndex(1);
        assertEquals(List.of(1, 0, 2), plan.ordered(Plan.Sort.LAST_OPENED));
    }

    @Test
    void lastEditedFallsBackToLastOpen() throws ReflectiveOperationException {
        final Plan plan = plan("A", "B", "C");
        at(plan, 0).setEditedAt(100);
        at(plan, 1).setEditedAt(0);
        at(plan, 1).setLastOpen(300);
        at(plan, 2).setEditedAt(200);
        assertEquals(List.of(1, 2, 0), plan.ordered(Plan.Sort.LAST_EDITED));
    }

    @Test
    void nameIgnoresCase() throws ReflectiveOperationException {
        final Plan plan = plan("beta", "Alpha", "gamma", "Beta2");
        assertEquals(List.of(1, 0, 3, 2), plan.ordered(Plan.Sort.NAME));
    }

    @Test
    void newestPutsPlansWithoutAMadeTimeLast() throws ReflectiveOperationException {
        final Plan plan = plan("A", "B", "C");
        at(plan, 0).setCreatedAt(100);
        at(plan, 1).setCreatedAt(0);
        at(plan, 2).setCreatedAt(200);
        assertEquals(List.of(2, 0, 1), plan.ordered(Plan.Sort.NEWEST));
    }

    @Test
    void anEditMovesEditedAtButSettingTheCameraDoesNot() {
        final Graph g = new Graph("A");
        g.setEditedAt(5);
        g.setZoom(2f);
        g.setPanX(10f);
        assertEquals(5, g.getEditedAt());
        g.setName("B");
        assertEquals(true, g.getEditedAt() > 5);
    }
}
