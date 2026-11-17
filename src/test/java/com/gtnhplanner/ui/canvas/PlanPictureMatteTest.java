package com.gtnhplanner.ui.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlanPictureMatteTest {

    private static int[] run(final int black, final int white) {
        final int[] b = { black }, w = { white };
        PlanPicture.matte(b, w);
        return new int[] { b[0] >>> 24, b[0] >> 16 & 0xFF, b[0] >> 8 & 0xFF, b[0] & 0xFF };
    }

    @Test
    void opaquePixelKeepsItsColour() {
        final int[] p = run(0x336699, 0x336699);
        assertEquals(255, p[0]);
        assertEquals(0x33, p[1]);
        assertEquals(0x66, p[2]);
        assertEquals(0x99, p[3]);
    }

    @Test
    void emptyPixelIsClear() {
        assertEquals(0, run(0x000000, 0xFFFFFF)[0]);
        final int[] b = { 0x000000 }, w = { 0xFFFFFF };
        PlanPicture.matte(b, w);
        assertEquals(0, b[0]);
    }

    @Test
    void halfWhiteIsHalfAlphaWhite() {
        final int[] p = run(0x808080, 0xFFFFFF);
        assertEquals(128, p[0], 1);
        for (int c = 1; c <= 3; c++) assertEquals(255, p[c], 1);
    }

    @Test
    void halfRedIsHalfAlphaRed() {
        final int[] p = run(0x800000, 0xFF8080);
        assertEquals(128, p[0], 2);
        assertEquals(255, p[1], 3);
        assertEquals(0, p[2], 2);
        assertEquals(0, p[3], 2);
    }
}
