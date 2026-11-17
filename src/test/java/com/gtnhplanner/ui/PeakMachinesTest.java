package com.gtnhplanner.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The machines a peak draw counts ({@link RailTotals#peakMachines}): a capped card draws for its whole set count. */
class PeakMachinesTest {

    private static final double DRAW = 100;

    @Test
    void cappedCardPeaksAtItsSetCountWhileAverageKeepsTheRunningOnes() {
        // Set to 4, the solver runs 2: peak draws for 4, average for 2.
        assertEquals(400, DRAW * RailTotals.peakMachines(2, 4), 1e-9);
        assertEquals(200, DRAW * 2, 1e-9);
    }

    @Test
    void unsetCardPeaksAtItsRunningMachinesRoundedUp() {
        assertEquals(300, DRAW * RailTotals.peakMachines(2.3, 0), 1e-9);
        assertEquals(200, DRAW * RailTotals.peakMachines(2.0000000001, 0), 1e-9);
    }

    @Test
    void runningPastTheSetCountKeepsTheRunningOnes() {
        assertEquals(5, RailTotals.peakMachines(4.2, 3), 1e-9);
    }

    @Test
    void nothingRunningDrawsNothingEvenWhenSet() {
        assertEquals(0, RailTotals.peakMachines(0, 4), 1e-9);
    }
}
