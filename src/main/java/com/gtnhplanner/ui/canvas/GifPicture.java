package com.gtnhplanner.ui.canvas;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * The looping GIF of a plan picture, the pure part (no Minecraft): the timing of its frames, a 256-colour palette
 * built from a picture, pixels mapped onto it, and the GIF written with a delay on every frame and the NETSCAPE loop
 * extension. The website's GIF has the same frame count, delay and side limit.
 */
public final class GifPicture {

    /** Frames in one loop, and the time each stays, as the website's GIF. */
    public static final int FRAMES = 24;
    public static final int DELAY_MS = 100;
    /** The largest GIF: the longer side, and pixels in all, so it stays a few megabytes. */
    public static final int MAX_SIDE = 1280;
    public static final long MAX_PIXELS = 1_300_000L;

    private GifPicture() {}

    /** How far through the loop frame {@code index} of {@code count} is, 0 to just under 1: even steps. */
    public static float phase(final int index, final int count) {
        return count <= 1 ? 0f : index / (float) count;
    }

    /** A delay in milliseconds as the GIF keeps it, in hundredths of a second (at least one). */
    public static int centiseconds(final int ms) {
        return Math.max(1, Math.round(ms / 10f));
    }

    // region Palette

    /**
     * Up to {@code max} colours that stand for the RGB pixels, by median cut over the colours' counts. A picture of
     * no more than {@code max} colours keeps every one of them exactly.
     */
    public static int[] palette(final int[] rgb, final int max) {
        final HashMap<Integer, int[]> counts = new HashMap<>();
        int last = -1;
        int[] lastCount = null;
        for (final int p : rgb) {
            final int c = p & 0xFFFFFF;
            if (lastCount != null && c == last) {
                lastCount[0]++;
                continue;
            }
            int[] n = counts.get(c);
            if (n == null) counts.put(c, n = new int[1]);
            n[0]++;
            last = c;
            lastCount = n;
        }
        final int size = counts.size();
        final int[] colours = new int[size], weight = new int[size];
        int i = 0;
        for (final Map.Entry<Integer, int[]> e : counts.entrySet()) {
            colours[i] = e.getKey();
            weight[i++] = e.getValue()[0];
        }
        if (size <= max) {
            Arrays.sort(colours);
            return colours.length == 0 ? new int[] { 0 } : colours;
        }
        final List<Integer[]> boxes = new ArrayList<>();
        final Integer[] all = new Integer[size];
        for (int k = 0; k < size; k++) all[k] = k;
        boxes.add(all);
        while (boxes.size() < max) {
            // Split the box with the widest spread, if any can be split.
            int best = -1, bestChannel = 0;
            long bestScore = 0;
            for (int b = 0; b < boxes.size(); b++) {
                final Integer[] box = boxes.get(b);
                if (box.length < 2) continue;
                final int[] lo = { 255, 255, 255 }, hi = { 0, 0, 0 };
                for (final int k : box) for (int ch = 0; ch < 3; ch++) {
                    final int v = colours[k] >> (16 - 8 * ch) & 0xFF;
                    lo[ch] = Math.min(lo[ch], v);
                    hi[ch] = Math.max(hi[ch], v);
                }
                for (int ch = 0; ch < 3; ch++) {
                    final long score = (long) (hi[ch] - lo[ch]) * (hi[ch] - lo[ch]) * Math.min(box.length, 64);
                    if (score > bestScore) {
                        bestScore = score;
                        best = b;
                        bestChannel = ch;
                    }
                }
            }
            if (best < 0) break;
            final Integer[] box = boxes.remove(best);
            final int shift = 16 - 8 * bestChannel;
            Arrays.sort(box, (a, b) -> Integer.compare(colours[a] >> shift & 0xFF, colours[b] >> shift & 0xFF));
            long total = 0;
            for (final int k : box) total += weight[k];
            long run = 0;
            int cut = 1;
            for (int k = 0; k < box.length - 1; k++) {
                run += weight[box[k]];
                cut = k + 1;
                if (run * 2 >= total) break;
            }
            boxes.add(Arrays.copyOfRange(box, 0, cut));
            boxes.add(Arrays.copyOfRange(box, cut, box.length));
        }
        final int[] out = new int[boxes.size()];
        for (int b = 0; b < out.length; b++) {
            long r = 0, g = 0, bl = 0, w = 0;
            for (final int k : boxes.get(b)) {
                r += (long) (colours[k] >> 16 & 0xFF) * weight[k];
                g += (long) (colours[k] >> 8 & 0xFF) * weight[k];
                bl += (long) (colours[k] & 0xFF) * weight[k];
                w += weight[k];
            }
            out[b] = (int) (r / w) << 16 | (int) (g / w) << 8 | (int) (bl / w);
        }
        return out;
    }

    /** The RGB pixels as indexes of their nearest colour in {@code palette}. */
    public static byte[] map(final int[] rgb, final int[] palette) {
        final byte[] out = new byte[rgb.length];
        final HashMap<Integer, Byte> seen = new HashMap<>();
        int last = -1;
        byte lastIndex = 0;
        boolean have = false;
        for (int i = 0; i < rgb.length; i++) {
            final int c = rgb[i] & 0xFFFFFF;
            if (!have || c != last) {
                Byte b = seen.get(c);
                if (b == null) seen.put(c, b = (byte) nearest(palette, c));
                last = c;
                lastIndex = b;
                have = true;
            }
            out[i] = lastIndex;
        }
        return out;
    }

    private static int nearest(final int[] palette, final int c) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        final int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
        for (int i = 0; i < palette.length; i++) {
            final int p = palette[i];
            final long dr = (p >> 16 & 0xFF) - r, dg = (p >> 8 & 0xFF) - g, db = (p & 0xFF) - b;
            final long d = 2 * dr * dr + 4 * dg * dg + 3 * db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
                if (d == 0) break;
            }
        }
        return best;
    }

    // endregion

    /**
     * Writes the frames (indexes into {@code palette}, {@code width * height} each) as a GIF that loops forever, each
     * frame {@code delayMs} long and drawn over the last one whole (so none smears into the next).
     */
    public static void write(final OutputStream out, final List<byte[]> frames, final int width, final int height,
        final int[] palette, final int delayMs) throws IOException {
        final int n = palette.length;
        final byte[] r = new byte[n], g = new byte[n], b = new byte[n];
        for (int i = 0; i < n; i++) {
            r[i] = (byte) (palette[i] >> 16);
            g[i] = (byte) (palette[i] >> 8);
            b[i] = (byte) palette[i];
        }
        int bits = 1;
        while (1 << bits < n) bits++;
        final IndexColorModel model = new IndexColorModel(bits, n, r, g, b);
        final ImageWriter writer = ImageIO.getImageWritersByFormatName("gif")
            .next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            final ImageWriteParam param = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);
            for (int f = 0; f < frames.size(); f++) {
                final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, model);
                final WritableRaster raster = image.getRaster();
                raster.setDataElements(0, 0, width, height, frames.get(f));
                final IIOMetadata meta = writer
                    .getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
                final String format = meta.getNativeMetadataFormatName();
                final IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                final IIOMetadataNode control = node(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "doNotDispose");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("transparentColorIndex", "0");
                control.setAttribute("delayTime", Integer.toString(centiseconds(delayMs)));
                if (f == 0) {
                    final IIOMetadataNode apps = node(root, "ApplicationExtensions");
                    final IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE");
                    app.setAttribute("authenticationCode", "2.0");
                    // Sub-block 1, then the loop count 0: for ever.
                    app.setUserObject(new byte[] { 1, 0, 0 });
                    apps.appendChild(app);
                }
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(image, null, meta), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static IIOMetadataNode node(final IIOMetadataNode root, final String name) {
        for (int i = 0; i < root.getLength(); i++) if (root.item(i)
            .getNodeName()
            .equals(name)) return (IIOMetadataNode) root.item(i);
        final IIOMetadataNode n = new IIOMetadataNode(name);
        root.appendChild(n);
        return n;
    }
}
