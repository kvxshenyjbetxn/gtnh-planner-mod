package com.gtnhplanner.ui.canvas;

import org.jetbrains.annotations.Nullable;

import com.gtnhplanner.data.flowchart.Drawer;
import com.gtnhplanner.data.flowchart.Edge;
import com.gtnhplanner.data.flowchart.Node;
import com.gtnhplanner.data.flowchart.balancer.BalanceResult;
import com.gtnhplanner.data.flowchart.balancer.Balancer;
import com.gtnhplanner.data.flowchart.balancer.PortRef;

/**
 * What the last solve carries on a wire (per second): the board's wires and the overview's selection read the same
 * numbers from here.
 */
public final class WireRates {

    private WireRates() {}

    /** What a wire between two cards carries: the solver's flow on it, else its source port's whole rate. */
    public static double edge(@Nullable final BalanceResult result, final Edge e) {
        if (result == null) return 0;
        if (result instanceof final BalanceResult.Solved solved && solved.auto() != null) {
            final Double f = solved.auto().edgeFlowsPerSecond.get(e.id);
            if (f != null) return f;
        }
        final Balancer.NodeBalance b = result.nodeBalances()
            .get(e.sourceNodeId);
        return b == null ? 0 : b.outputPerSecond(e.sourceOutputIndex);
    }

    /** What passes through one of a buffer's ports: the solver's flow on its edges there, not the port's whole rate. */
    public static double buffer(@Nullable final BalanceResult result, final Drawer d, final PortRef port) {
        if (!(result instanceof final BalanceResult.Solved solved) || solved.auto() == null) return 0;
        return solved.auto().drawers.portRate(d.getId(), port);
    }

    /** What a drawer's link carries: its port's rate. */
    public static double link(@Nullable final BalanceResult result, final Node n, final boolean input, final int port) {
        if (result == null) return 0;
        final Balancer.NodeBalance b = result.nodeBalances()
            .get(n.id);
        if (b == null) return 0;
        return input ? b.inputPerSecond(port) : b.outputPerSecond(port);
    }
}
