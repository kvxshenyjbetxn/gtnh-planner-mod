package com.gtnhplanner.nei;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.ui.BoardSession;

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
}
