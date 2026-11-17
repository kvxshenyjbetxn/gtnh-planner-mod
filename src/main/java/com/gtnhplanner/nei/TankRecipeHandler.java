package com.gtnhplanner.nei;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhplanner.Compat;
import com.gtnhplanner.data.provider.gregtech.GTHooks;
import com.gtnhplanner.power.FluidTank;
import com.gtnhplanner.ui.card.CardDefaults;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI's "Tank" tab: the fluid tank card's free recipes (a filled cell into its fluid and the empty cell, or the other
 * way), shown only on a page the planner opened from a card's port, and only the one direction that fits that port.
 * "+" adds the tank card ({@link PlanOverlayHandler}, {@link #node}), placed and wired as any recipe added from the
 * lookup.
 */
public class TankRecipeHandler extends TemplateRecipeHandler {

    public static final String ID = "gtnhplanner.tank";

    /** One recipe: a filled container and what it holds, filled or emptied. */
    public record Entry(boolean emptying, ItemStack cell, FluidStack fluid, ItemStack empty) {}

    private final List<Entry> entries = new ArrayList<>();

    public TankRecipeHandler() {}

    /**
     * What a looked-up stack makes ({@code uses} false) or feeds ({@code uses} true) in the tank; none for anything
     * but a fluid or a filled container, and none at all while the planner has not armed this lookup.
     */
    public static List<Entry> entriesFor(@Nullable final ItemStack looked, final boolean uses) {
        final List<Entry> out = new ArrayList<>();
        if (looked == null || !PlannerTabs.armed()) return out;
        final ItemStack one = looked.copy();
        one.stackSize = 1;
        if (FluidContainerRegistry.isFilledContainer(one)) {
            final Entry e = of(one);
            // Made: a cell is filled; used: it is emptied.
            if (e != null) out.add(new Entry(uses, e.cell, e.fluid, e.empty));
            return out;
        }
        final FluidStack fluid = StackInfo.getFluid(one);
        if (fluid == null || fluid.getFluid() == null) return out;
        final String key = "fluid:" + fluid.getFluid()
            .getName();
        final String preferred = FluidTank.cellFor(key);
        Entry first = null;
        for (final FluidContainerRegistry.FluidContainerData data : FluidContainerRegistry
            .getRegisteredFluidContainerData()) {
            if (data == null || data.fluid == null
                || data.filledContainer == null
                || data.emptyContainer == null
                || !data.fluid.isFluidEqual(fluid)) continue;
            final Entry e = of(data.filledContainer);
            if (e == null) continue;
            // Made: the fluid comes out of cells; used: it goes into them.
            final Entry made = new Entry(!uses, e.cell, e.fluid, e.empty);
            if (first == null && CardDefaults.itemKey(e.cell)
                .equals(preferred)) first = made;
            else out.add(made);
        }
        if (first != null) out.add(0, first);
        return out;
    }

    @Nullable
    private static Entry of(final ItemStack cell) {
        final String key = CardDefaults.itemKey(cell);
        final FluidTank.Pair pair = FluidTank.pair(key);
        return pair == null ? null : new Entry(true, pair.cell(), pair.fluid(), pair.empty());
    }

    /** The tank card for a recipe, its ports built; null when its container is no longer known. */
    @Nullable
    public com.gtnhplanner.data.flowchart.Node node(final int recipe) {
        if (recipe < 0 || recipe >= entries.size()) return null;
        final Entry e = entries.get(recipe);
        return FluidTank.make(e.emptying, CardDefaults.itemKey(e.cell));
    }

    private final class Cached extends CachedRecipe {

        private final List<PositionedStack> in = new ArrayList<>(), more = new ArrayList<>();
        private final PositionedStack result;

        Cached(final Entry e) {
            final ItemStack litres = Compat.GREGTECH.isLoaded ? GTHooks.fluidDisplayStackWithAmount(e.fluid) : null;
            final ItemStack fluid = litres == null ? e.cell : litres;
            if (e.emptying) {
                in.add(new PositionedStack(e.cell, 28, 16, false));
                result = new PositionedStack(fluid, 108, 16, false);
                more.add(new PositionedStack(e.empty, 130, 16, false));
            } else {
                in.add(new PositionedStack(fluid, 6, 16, false));
                in.add(new PositionedStack(e.empty, 28, 16, false));
                result = new PositionedStack(e.cell, 130, 16, false);
            }
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return in;
        }

        @Override
        public PositionedStack getResult() {
            return result;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return more;
        }
    }

    private void load(final ItemStack stack, final boolean uses) {
        ensureInfo();
        for (final Entry e : entriesFor(stack, uses)) {
            entries.add(e);
            arecipes.add(new Cached(e));
        }
    }

    @Override
    public void loadCraftingRecipes(final ItemStack result) {
        load(result, false);
    }

    @Override
    public void loadUsageRecipes(final ItemStack ingredient) {
        load(ingredient, true);
    }

    @Override
    public String getGuiTexture() {
        return "textures/gui/container/inventory.png";
    }

    @Override
    public String getRecipeName() {
        return "Tank";
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    /** A plain method, not an override: NEI before its handler ids has none. */
    public String getHandlerId() {
        return ID;
    }

    @Override
    public void drawBackground(final int recipe) {
        if (recipe < 0 || recipe >= arecipes.size()) return;
        PlannerTabs.panel(0, 0, 166, 36);
        PlannerTabs.headerBand(1, 1, 164, 12);
        final CachedRecipe c = arecipes.get(recipe);
        for (final PositionedStack s : c.getIngredients()) PlannerTabs.slot(s.relx, s.rely);
        for (final PositionedStack s : c.getOtherStacks()) PlannerTabs.slot(s.relx, s.rely);
        final PositionedStack r = c.getResult();
        if (r != null) PlannerTabs.slot(r.relx, r.rely);
    }

    @Override
    public void drawExtras(final int recipe) {
        final boolean emptying = recipe >= 0 && recipe < entries.size()
            && entries.get(recipe)
                .emptying();
        final String label = emptying ? "Empty cells" : "Fill cells";
        GuiDraw.drawString(
            label,
            4,
            3,
            PlannerTabs.theme()
                .name(),
            false);
        // Beside the label, not at the band's right end: NEI's favourite button sits there.
        GuiDraw.drawString(
            "- free, instant",
            4 + GuiDraw.fontRenderer.getStringWidth(label) + 4,
            3,
            PlannerTabs.theme()
                .free(),
            false);
        // An arrow between inputs and outputs.
        final int y = 23;
        final int arrow = PlannerTabs.theme()
            .arrow();
        GuiDraw.drawRect(56, y, 28, 2, arrow);
        for (int i = 0; i < 4; i++) GuiDraw.drawRect(82 + i, y - 4 + i + 1, 1, 8 - 2 * i - 1, arrow);
    }

    /** Tells NEI's tab strip this handler's icon and size; NEI forgets them whenever it reloads its handler list. */
    private static void ensureInfo() {
        PlannerTabs.ensureInfo(
            ID,
            com.gtnhplanner.power.game.PowerPorts.machineStack(FluidTank.ID),
            "minecraft:bucket",
            40,
            166,
            5);
    }
}
