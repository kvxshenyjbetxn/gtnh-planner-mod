package com.gtnhplanner.ui.canvas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.NodeList;

class GifPictureTest {

    private static final String IMAGE = "javax_imageio_gif_image_1.0";

    @Test
    void phasesStepEvenlyAndNeverReachOne() {
        assertEquals(0f, GifPicture.phase(0, 24));
        assertEquals(0.5f, GifPicture.phase(12, 24));
        assertTrue(GifPicture.phase(23, 24) < 1f);
        assertEquals(10, GifPicture.centiseconds(100));
        assertEquals(1, GifPicture.centiseconds(0));
    }

    @Test
    void fewColoursKeepTheirOwnPalette() {
        final int[] pixels = { 0x112233, 0x112233, 0xFF0000, 0x00FF00, 0xFF0000 };
        assertArrayEquals(new int[] { 0x00FF00, 0x112233, 0xFF0000 }, GifPicture.palette(pixels, 256));
    }

    @Test
    void manyColoursFitTheLimit() {
        final int[] pixels = new int[4096];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i * 4093 & 0xFFFFFF;
        final int[] palette = GifPicture.palette(pixels, 256);
        assertTrue(palette.length <= 256 && palette.length > 100);
        assertEquals(pixels.length, GifPicture.map(pixels, palette).length);
    }

    @Test
    void writesALoopingGifWithDelayAndFrames(@TempDir final Path dir) throws Exception {
        final int w = 4, h = 3;
        final int[] palette = { 0x000000, 0xFF0000, 0x00FF00, 0x0000FF };
        final List<byte[]> frames = List.of(new byte[w * h], filled(w * h, 1), filled(w * h, 2));
        final File file = dir.resolve("t.gif")
            .toFile();
        try (OutputStream out = new FileOutputStream(file)) {
            GifPicture.write(out, frames, w, h, palette, 100);
        }
        final ImageReader reader = ImageIO.getImageReadersByFormatName("gif")
            .next();
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            reader.setInput(in);
            assertEquals(3, reader.getNumImages(true));
            assertEquals(w, reader.getWidth(0));
            for (int i = 0; i < 3; i++) {
                final IIOMetadataNode m = (IIOMetadataNode) reader.getImageMetadata(i)
                    .getAsTree(IMAGE);
                final IIOMetadataNode control = (IIOMetadataNode) m.getElementsByTagName("GraphicControlExtension")
                    .item(0);
                assertEquals("10", control.getAttribute("delayTime"));
            }
            final IIOMetadataNode first = (IIOMetadataNode) reader.getImageMetadata(0)
                .getAsTree(IMAGE);
            final NodeList apps = first.getElementsByTagName("ApplicationExtension");
            assertEquals(1, apps.getLength());
            final IIOMetadataNode app = (IIOMetadataNode) apps.item(0);
            assertEquals("NETSCAPE", app.getAttribute("applicationID"));
            assertEquals("2.0", app.getAttribute("authenticationCode"));
            assertArrayEquals(new byte[] { 1, 0, 0 }, (byte[]) app.getUserObject());
            assertEquals(
                0xFF0000,
                reader.read(1)
                    .getRGB(0, 0) & 0xFFFFFF);
        } finally {
            reader.dispose();
        }
    }

    private static byte[] filled(final int n, final int v) {
        final byte[] b = new byte[n];
        Arrays.fill(b, (byte) v);
        return b;
    }
}
