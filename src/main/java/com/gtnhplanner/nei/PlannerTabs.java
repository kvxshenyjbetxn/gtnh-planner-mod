package com.gtnhplanner.nei;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

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

    private static final int BORDER = 0xFF8E8E8E;

    /** A card: light fill with a 1 px border. */
    static void panel(final int x, final int y, final int w, final int h) {
        GuiDraw.drawRect(x, y, w, h, BORDER);
        GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, 0xFFD8D8D8);
    }

    /** A darker band along a card's top, a 1 px line under it. */
    static void headerBand(final int x, final int y, final int w, final int h) {
        GuiDraw.drawRect(x, y, w, h, 0xFFBCBCBC);
        GuiDraw.drawRect(x, y + h, w, 1, BORDER);
    }

    /** A vanilla inset slot behind a 16 px item drawn at (x, y). */
    static void slot(final int x, final int y) {
        GuiDraw.drawRect(x - 1, y - 1, 18, 18, 0xFF8B8B8B);
        GuiDraw.drawRect(x - 1, y - 1, 18, 1, 0xFF373737);
        GuiDraw.drawRect(x - 1, y - 1, 1, 18, 0xFF373737);
        GuiDraw.drawRect(x - 1, y + 16, 18, 1, 0xFFFFFFFF);
        GuiDraw.drawRect(x + 16, y - 1, 1, 18, 0xFFFFFFFF);
    }
}
