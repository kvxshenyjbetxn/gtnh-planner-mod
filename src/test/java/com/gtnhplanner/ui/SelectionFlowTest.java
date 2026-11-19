package com.gtnhplanner.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.ui.SelectionFlow.Held;
import com.gtnhplanner.ui.SelectionFlow.Open;
import com.gtnhplanner.ui.SelectionFlow.Role;
import com.gtnhplanner.ui.SelectionFlow.Wire;

/** The overview's selection lists ({@link SelectionFlow}), as the website's selection-flow test has them. */
class SelectionFlowTest {

    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();

    @Test
    void wiresAreSortedByWhereTheyCrossTheSelection() {
        final var f = SelectionFlow.measure(
            Set.of(b),
            List.of(new Wire(a, b, "in", 2), new Wire(b, c, "out", 3), new Wire(a, c, "far", 9)),
            List.of(),
            List.of());
        assertEquals(
            2.0,
            f.inputs()
                .get("in"));
        assertEquals(
            3.0,
            f.outputs()
                .get("out"));
        assertTrue(
            f.internal()
                .isEmpty());
        assertEquals(
            2,
            f.inputs()
                .size()
                + f.outputs()
                    .size());
    }

    @Test
    void aWireInsideIsInternalAndRatesOfOneResourceAdd() {
        final var f = SelectionFlow.measure(
            Set.of(a, b),
            List.of(new Wire(a, b, "x", 1), new Wire(a, b, "x", 2), new Wire(b, c, "x", 4)),
            List.of(),
            List.of());
        assertEquals(
            3.0,
            f.internal()
                .get("x"));
        assertEquals(
            4.0,
            f.outputs()
                .get("x"));
    }

    @Test
    void openPortsAndDrawersCrossTheEdgeToo() {
        final var f = SelectionFlow.measure(
            Set.of(a, d),
            List.of(),
            List.of(new Open(a, true, "ore", 5), new Open(a, false, "dust", 1), new Open(c, true, "no", 7)),
            List.of(
                new Held(d, Role.SOURCE, "water", 8),
                new Held(d, Role.PRODUCT, "plate", 2),
                new Held(c, Role.SOURCE, "no", 7),
                new Held(d, Role.OTHER, "trash", 9)));
        assertEquals(
            List.of("ore", "water"),
            List.copyOf(
                f.inputs()
                    .keySet()));
        assertEquals(
            List.of("dust", "plate"),
            List.copyOf(
                f.outputs()
                    .keySet()));
    }

    @Test
    void zeroRatesAreLeftOut() {
        final var f = SelectionFlow.measure(Set.of(a), List.of(new Wire(a, b, "x", 0)), List.of(), List.of());
        assertTrue(
            f.outputs()
                .isEmpty());
    }
}
