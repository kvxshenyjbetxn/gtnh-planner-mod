package com.gtnhplanner.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.data.flowchart.Node;
import com.gtnhplanner.data.flowchart.Port;
import com.gtnhplanner.data.provider.gregtech.GTHooks;
import com.gtnhplanner.power.Energy;
import com.gtnhplanner.power.PowerSearch;
import com.gtnhplanner.power.game.PowerPorts;
import com.gtnhplanner.ui.theme.Fmt;
import com.gtnhplanner.ui.theme.Hyb;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI's "Generators" tab: the power sources (generators, engines, turbines, boilers) that burn the looked-up resource
 * ("uses") or make it ("recipes"), one entry per source at the fuel setting that matches, with its rates. Shown only on
 * a page the planner opened from a card's port. "+" adds the power card ({@link PlanOverlayHandler}, {@link #node}),
 * placed and wired as any recipe added from the lookup.
 */
public class GeneratorRecipeHandler extends TemplateRecipeHandler {

    public static final String ID = "gtnhplanner.generators";

    private static final int WIDTH = 166, HEIGHT = 50, PER_PAGE = 3;
    private static final int ROWS = 2;
    private static final int[] ROW_Y = { 13, 31 };
    private static final int LEFT_SLOT = 2, LEFT_TEXT = 21, ARROW_W = 14, RIGHT_LIMIT = WIDTH - 14;
    /** The right text keeps at least this much room, so the left text room is capped. */
    private static final int MIN_RIGHT_TEXT = 44;

    /** Where an entry's columns sit, from its own text widths. */
    private record Layout(int leftRoom, int arrowX, int rightSlotX, int rightTextX) {}

    private static Layout layout(final net.minecraft.client.gui.FontRenderer font, final Entry e) {
        final int fixed = LEFT_TEXT + 2 + ARROW_W + 2 + 16 + 3;
        final int cap = Math.max(0, RIGHT_LIMIT - MIN_RIGHT_TEXT - fixed);
        int room = 0;
        for (int i = 0; i < ROWS && i < e.inputs()
            .size(); i++) {
            final int extra = e.inputs()
                .size() - ROWS;
            final String tail = i == ROWS - 1 && extra > 0 ? " +" + extra : "";
            room = Math.max(
                room,
                font.getStringWidth(
                    e.inputs()
                        .get(i)
                        .rate() + tail));
        }
        room = Math.min(room, cap);
        final int arrowX = LEFT_TEXT + room + 2;
        final int slotX = arrowX + ARROW_W + 2;
        return new Layout(room, arrowX, slotX, slotX + 16 + 3);
    }

    /** One flow of an entry: what it is as NEI shows it (null for EU) and its rate as text. */
    public record Flow(@Nullable ItemStack stack, String rate) {}

    /** One source at a setting, with its flows read off the card it would make. */
    public record Entry(String sourceId, Map<String, String> settings, String name, String tier, List<Flow> inputs,
        List<Flow> outputs) {}

    private final List<Entry> entries = new ArrayList<>();

    public GeneratorRecipeHandler() {}

    /** The sources that burn ({@code uses}) or make a looked-up stack; none while the planner has not armed it. */
    public static List<Entry> entriesFor(@Nullable final ItemStack looked, final boolean uses) {
        final List<Entry> out = new ArrayList<>();
        if (looked == null || !PlannerTabs.armed()) return out;
        final ItemStack one = looked.copy();
        one.stackSize = 1;
        final List<String[]> clauses = new ArrayList<>();
        final FluidStack fluid = StackInfo.getFluid(one);
        if (fluid != null && fluid.getFluid() != null) {
            clauses.add(
                new String[] { "fluid", fluid.getFluid()
                    .getName() });
        } else if (one.getItem() != null) {
            final String name = String.valueOf(Item.itemRegistry.getNameForObject(one.getItem()));
            final String suffix = one.getItemDamage() == 0 ? "" : "@" + one.getItemDamage();
            clauses.add(new String[] { "item", name + suffix });
            // The website writes mod ids in lower case.
            final int colon = name.indexOf(':');
            if (colon > 0) {
                final String lower = name.substring(0, colon)
                    .toLowerCase(Locale.ROOT) + name.substring(colon)
                    + suffix;
                if (!lower.equals(name + suffix)) clauses.add(new String[] { "item", lower });
            }
        }
        final List<String> seen = new ArrayList<>();
        for (final String[] c : clauses) {
            try {
                final List<PowerSearch.StencilHit> hits = PowerSearch.searchForStencil(
                    List.of(new PowerSearch.StencilClause(uses, c[0], c[1])),
                    PowerSearch.Op.ALL,
                    PowerSearch.Op.ALL,
                    "");
                for (final PowerSearch.StencilHit hit : hits) {
                    final Map<String, String> settings = hit.settings() == null ? Map.of() : hit.settings();
                    final String key = hit.source()
                        .id() + settings;
                    if (seen.contains(key)) continue;
                    seen.add(key);
                    final Entry e = entry(hit.source(), settings);
                    if (e != null) out.add(e);
                }
            } catch (final RuntimeException | LinkageError e) {
                GtnhPlanner.LOG.warn("Generators tab could not search for {}", c[1], e);
            }
        }
        return out;
    }

    @Nullable
    private static Entry entry(final com.gtnhplanner.power.PowerSource source, final Map<String, String> settings) {
        final Node card = Node.power(source.id(), settings, 0, 0);
        final List<Flow> in = new ArrayList<>(), outs = new ArrayList<>();
        for (final Port<?> p : card.inputs) {
            final Flow f = flow(p);
            if (f != null) in.add(f);
        }
        // EU has no stack of its own: it shows as the machine's icon, which also gives NEI the output stack it wants
        // before it makes the recipe's buttons.
        ItemStack machine = PowerPorts.machineStack(source.id());
        if (machine == null) machine = new ItemStack(Items.redstone);
        for (final Port<?> p : card.outputs) {
            final Flow f = flow(p);
            if (f != null) outs.add(f.stack() == null ? new Flow(machine, f.rate()) : f);
        }
        // A source that builds no output port (Eye of Harmony) still gets a slot, so NEI makes its buttons.
        if (outs.isEmpty()) outs.add(new Flow(machine, ""));
        return new Entry(
            source.id(),
            Map.copyOf(settings),
            source.name(),
            source.unlock() == null ? "" : source.unlock(),
            in,
            outs);
    }

    @Nullable
    private static Flow flow(final Port<?> port) {
        final double perSecond = port.amount();
        final Object value = port.getValue();
        if (value instanceof Energy) return new Flow(null, Fmt.compact(perSecond / 20) + " EU/t");
        if (value instanceof final FluidStack fluid) {
            final ItemStack shown = com.gtnhplanner.Compat.GREGTECH.isLoaded ? GTHooks.fluidDisplayStack(fluid) : null;
            return shown == null ? null : new Flow(shown, Fmt.compact(perSecond) + " L/s");
        }
        if (value instanceof final ItemStack item) {
            final ItemStack shown = item.copy();
            shown.stackSize = 1;
            return new Flow(shown, Fmt.compact(perSecond) + "/s");
        }
        return null;
    }

    /** The power card for an entry, its ports built. */
    @Nullable
    public Node node(final int recipe) {
        if (recipe < 0 || recipe >= entries.size()) return null;
        final Entry e = entries.get(recipe);
        return Node.power(e.sourceId(), e.settings(), 0, 0);
    }

    private final class Cached extends CachedRecipe {

        private final List<PositionedStack> in = new ArrayList<>(), more = new ArrayList<>();

        private final Layout layout;

        Cached(final Entry e) {
            layout = layout(GuiDraw.fontRenderer, e);
            for (int i = 0; i < ROWS && i < e.inputs()
                .size(); i++) {
                final Flow f = e.inputs()
                    .get(i);
                if (f.stack() != null) in.add(new PositionedStack(f.stack(), LEFT_SLOT, ROW_Y[i], false));
            }
            for (int i = 0; i < ROWS && i < e.outputs()
                .size(); i++) {
                final Flow f = e.outputs()
                    .get(i);
                if (f.stack() != null) more.add(new PositionedStack(f.stack(), layout.rightSlotX(), ROW_Y[i], false));
            }
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return in;
        }

        @Override
        public PositionedStack getResult() {
            return null;
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
        return "Generators";
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
    public void drawBackground(final int recipe) {}

    private static String fit(final net.minecraft.client.gui.FontRenderer font, final String text, final int room) {
        if (font.getStringWidth(text) <= room) return text;
        return font.trimStringToWidth(text, Math.max(0, room - font.getStringWidth("..."))) + "...";
    }

    /**
     * One side's rows: the slot is drawn by NEI, the rate beside it; EU has no slot, so its text takes the slot's
     * place.
     */
    private static void drawSide(final net.minecraft.client.gui.FontRenderer font, final List<Flow> flows,
        final int slotX, final int textX, final int right) {
        for (int i = 0; i < ROWS && i < flows.size(); i++) {
            final Flow f = flows.get(i);
            String text = f.rate();
            if (text.isEmpty()) continue;
            final int extra = flows.size() - ROWS;
            final int x = f.stack() == null ? slotX : textX;
            final String tail = i == ROWS - 1 && extra > 0 ? " +" + extra : "";
            text = fit(font, text, right - x - font.getStringWidth(tail)) + tail;
            GuiDraw.drawString(text, x, ROW_Y[i] + 4, 0xFF404040, false);
        }
    }

    @Override
    public void drawExtras(final int recipe) {
        if (recipe < 0 || recipe >= entries.size()) return;
        final Entry e = entries.get(recipe);
        final net.minecraft.client.gui.FontRenderer font = GuiDraw.fontRenderer;
        // Name on the left, the tier as a small chip at the right; the name shortens to leave room for it.
        int chip = 0;
        if (!e.tier()
            .isEmpty()) {
            final Hyb.Tier t = Hyb.tier(e.tier());
            chip = font.getStringWidth(e.tier()) + 4;
            GuiDraw.drawRect(WIDTH - chip, 1, chip, 10, 0xFF000000 | t.bg());
            GuiDraw.drawString(e.tier(), WIDTH - chip + 2, 2, 0xFF000000 | t.text(), false);
        }
        GuiDraw.drawString(fit(font, e.name(), WIDTH - 2 - (chip == 0 ? 0 : chip + 4)), 2, 2, 0xFF404040, false);
        // An arrow between inputs and outputs, level with the middle of the rows.
        final Layout l = ((Cached) arecipes.get(recipe)).layout;
        final int y = (ROW_Y[0] + ROW_Y[ROWS - 1] + 16) / 2;
        GuiDraw.drawRect(l.arrowX(), y - 1, 10, 2, 0xFF8B8B8B);
        for (int i = 0; i < 4; i++) GuiDraw.drawRect(l.arrowX() + 10 + i, y - 4 + i, 1, 8 - 2 * i, 0xFF8B8B8B);
        drawSide(font, e.inputs(), LEFT_SLOT, LEFT_TEXT, LEFT_TEXT + l.leftRoom());
        // Stop short of NEI's column of buttons at the right edge.
        drawSide(font, e.outputs(), l.rightSlotX(), l.rightTextX(), RIGHT_LIMIT);
    }

    /** Tells NEI's tab strip this handler's icon and size; the Combustion Generator, else a redstone dust. */
    private static void ensureInfo() {
        PlannerTabs.ensureInfo(
            ID,
            PowerPorts.machineStack("combustion-generator"),
            "minecraft:redstone",
            HEIGHT,
            WIDTH,
            PER_PAGE);
    }
}
