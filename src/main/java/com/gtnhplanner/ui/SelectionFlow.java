package com.gtnhplanner.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the overview shows while cards, drawers or a group are selected (the website's {@code measureSelectionFlow}):
 * the solve's flows counted by where they cross the selection. Pure: the session gives it the solved rates.
 *
 * <p>
 * A wire with both ends inside the selection is internal; one that ends inside it is an input, one that starts inside
 * it an output; a wire touching it nowhere counts for nothing. A selected card's port that nothing is wired to, or the
 * part of a wired port's rate the solve sends out of the plan or brings into it (an open gate), crosses the edge too.
 * A selected source drawer brings its rate in, a selected product drawer sends it out.
 */
final class SelectionFlow {

    private SelectionFlow() {}

    /** One solved wire between two cards or drawers (a drawer's link is a wire to its card). */
    record Wire(UUID from, UUID to, String key, double rate) {}

    /** What crosses the plan's edge at a card's port: an unwired port's rate, or an open gate's. */
    record Open(UUID node, boolean input, String key, double rate) {}

    /** What a drawer is to the plan, as it decides which edge its own rate crosses. */
    enum Role {
        SOURCE,
        PRODUCT,
        /** Trash and buffers: only their wires count. */
        OTHER
    }

    /** A drawer's own rate through the plan's edge. */
    record Held(UUID drawer, Role role, String key, double rate) {}

    /** Per resource key, a rate (per second), in the order the plan met them. */
    record Flows(Map<String, Double> inputs, Map<String, Double> outputs, Map<String, Double> internal) {}

    static Flows measure(final Set<UUID> selected, final List<Wire> wires, final List<Open> opens,
        final List<Held> drawers) {
        final Map<String, Double> in = new LinkedHashMap<>(), out = new LinkedHashMap<>(),
            internal = new LinkedHashMap<>();
        for (final Wire w : wires) {
            final boolean from = selected.contains(w.from()), to = selected.contains(w.to());
            if (!from && !to) continue;
            add(from && to ? internal : to ? in : out, w.key(), w.rate());
        }
        for (final Open o : opens) if (selected.contains(o.node())) add(o.input() ? in : out, o.key(), o.rate());
        for (final Held d : drawers) {
            if (!selected.contains(d.drawer())) continue;
            if (d.role() == Role.SOURCE) add(in, d.key(), d.rate());
            else if (d.role() == Role.PRODUCT) add(out, d.key(), d.rate());
        }
        return new Flows(in, out, internal);
    }

    private static void add(final Map<String, Double> into, final String key, final double rate) {
        if (!(rate > 0)) return;
        into.merge(key, rate, Double::sum);
    }
}
