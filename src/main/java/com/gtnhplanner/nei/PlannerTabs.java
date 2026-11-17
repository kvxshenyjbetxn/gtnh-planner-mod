package com.gtnhplanner.nei;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.ui.BoardSession;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;

/** What the planner's own NEI tabs (Tank, Generators) share: when they show, and their tab strip entry. */
final class PlannerTabs {

    private PlannerTabs() {}

    /** Whether the planner armed this lookup from a card's port; the tabs list nothing otherwise. */
    static boolean armed() {
        final BoardSession session = BoardSession.current();
        return session != null && session.lookupArmed();
    }

    /**
     * Tells NEI's tab strip a handler's icon and size; NEI forgets them whenever it reloads its handler list, so
     * handlers call this each time they load. {@code icon} may be null (then {@code fallback}, an item registry name).
     */
    static void ensureInfo(final String id, @Nullable final ItemStack icon, final String fallback, final int height,
        final int width, final int perPage) {
        try {
            if (GuiRecipeTab.handlerMap.containsKey(id)) return;
            final HandlerInfo info = new HandlerInfo(id, "GTNH Planner", GtnhPlanner.MODID, false, null);
            // Height, width, recipes a page.
            info.setHandlerDimensions(height, width, perPage);
            String name = icon == null ? null : String.valueOf(Item.itemRegistry.getNameForObject(icon.getItem()));
            int meta = icon == null ? 0 : icon.getItemDamage();
            if (name == null || name.equals("null")) {
                name = fallback;
                meta = 0;
            }
            info.setItem(name + ":" + meta, "");
            GuiRecipeTab.handlerMap.put(id, info);
        } catch (final LinkageError | RuntimeException e) {
            GtnhPlanner.LOG.warn("Could not give NEI's {} tab an icon", id, e);
        }
    }

    /** Every colour the tabs draw with, picked to suit NEI's page under the active resource pack. */
    record Theme(boolean dark, int panel, int border, int band, int divider, int arrow, int slotFill, int slotDark,
        int slotLight, int name, int rate, int resource, int eu, int free) {}

    private static final Theme LIGHT = new Theme(
        false,
        0xFFD8D8D8,
        0xFF8E8E8E,
        0xFFBCBCBC,
        0xFFB0B0B0,
        0xFF6E6E6E,
        0xFF8B8B8B,
        0xFF373737,
        0xFFFFFFFF,
        0xFF202020,
        0xFF303030,
        0xFF707070,
        0xFF7A5200,
        0xFF505050);
    private static final Theme DARK = new Theme(
        true,
        0xFF2A2A2A,
        0xFF555555,
        0xFF383838,
        0xFF4A4A4A,
        0xFF8C8C8C,
        0xFF1C1C1C,
        0xFF0A0A0A,
        0xFF4A4A4A,
        0xFFE8E8E8,
        0xFFD6D6D6,
        0xFF9C9C9C,
        0xFFFFC857,
        0xFF9C9C9C);

    private static volatile Theme theme;
    private static boolean listening;

    /** The palette for the active resource pack, decided once per resource reload. */
    static Theme theme() {
        final Theme t = theme;
        if (t != null) return t;
        listen();
        return theme = decide();
    }

    private static void listen() {
        if (listening) return;
        listening = true;
        try {
            final IResourceManager manager = Minecraft.getMinecraft()
                .getResourceManager();
            if (manager instanceof IReloadableResourceManager reloadable) {
                reloadable.registerReloadListener(m -> theme = null);
            }
        } catch (final RuntimeException | LinkageError e) {
            GtnhPlanner.LOG.warn("Could not watch for resource reloads", e);
        }
    }

    private static Theme decide() {
        final String forced = System.getProperty("gtnhplanner.neiTheme");
        final boolean dark;
        if ("dark".equalsIgnoreCase(forced)) dark = true;
        else if ("light".equalsIgnoreCase(forced)) dark = false;
        else dark = pageIsDark();
        final Theme base = dark ? DARK : LIGHT;
        final int text = packText();
        if (text == 0) return base;
        return new Theme(
            base.dark,
            base.panel,
            base.border,
            base.band,
            base.divider,
            base.arrow,
            base.slotFill,
            base.slotDark,
            base.slotLight,
            text,
            text,
            base.resource,
            base.eu,
            base.free);
    }

    /** Average luminance of the opaque pixels in the middle of NEI's recipe page texture; light on any failure. */
    private static boolean pageIsDark() {
        try {
            final IResource res = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(new ResourceLocation("nei", "textures/gui/recipebg.png"));
            try (InputStream in = res.getInputStream()) {
                final BufferedImage img = ImageIO.read(in);
                if (img == null) return false;
                final int w = img.getWidth(), h = img.getHeight();
                double sum = 0;
                int n = 0;
                for (int y = h / 4; y < h - h / 4; y++) {
                    for (int x = w / 4; x < w - w / 4; x++) {
                        final int argb = img.getRGB(x, y);
                        if ((argb >>> 24) < 128) continue;
                        sum += (0.2126 * ((argb >> 16) & 255) + 0.7152 * ((argb >> 8) & 255) + 0.0722 * (argb & 255))
                            / 255.0;
                        n++;
                    }
                }
                return n > 0 && sum / n < 0.45;
            }
        } catch (final IOException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** NEI's own text colour when the pack moved it off the 0x404040 default, else 0 (older NEI: 0). */
    private static int packText() {
        try {
            final Object res = Class.forName("codechicken.nei.ColorUtils")
                .getField("textGray")
                .get(null);
            final int rgb = ((Integer) res.getClass()
                .getMethod("getColor")
                .invoke(res)) & 0xFFFFFF;
            return rgb == 0x404040 ? 0 : 0xFF000000 | rgb;
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 0;
        }
    }

    /** A card: filled with a 1 px border. */
    static void panel(final int x, final int y, final int w, final int h) {
        final Theme t = theme();
        GuiDraw.drawRect(x, y, w, h, t.border());
        GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, t.panel());
    }

    /** A band along a card's top, a 1 px line under it. */
    static void headerBand(final int x, final int y, final int w, final int h) {
        final Theme t = theme();
        GuiDraw.drawRect(x, y, w, h, t.band());
        GuiDraw.drawRect(x, y + h, w, 1, t.border());
    }

    /** An inset slot behind a 16 px item drawn at (x, y). */
    static void slot(final int x, final int y) {
        final Theme t = theme();
        GuiDraw.drawRect(x - 1, y - 1, 18, 18, t.slotFill());
        GuiDraw.drawRect(x - 1, y - 1, 18, 1, t.slotDark());
        GuiDraw.drawRect(x - 1, y - 1, 1, 18, t.slotDark());
        GuiDraw.drawRect(x - 1, y + 16, 18, 1, t.slotLight());
        GuiDraw.drawRect(x + 16, y - 1, 1, 18, t.slotLight());
    }
}
