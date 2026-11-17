package com.gtnhplanner.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.gui.FontRenderer;
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

    private static final int WIDTH = 166, ROW = 18, BAND = 12, BAND2 = 22, GAP = 4, DIVIDER = 5, TEXT_X = 24,
        TEXT_END = 150, SLOT_X = 4;
    private static final int PER_PAGE = 3;
    /** The smallest entry (no input, one output) is what NEI's tab strip is told; taller entries say so themselves. */
    private static final int HEIGHT = cardHeight(BAND, 0, 1) + GAP;

    /** A card's height (without the gap below): band, rows, divider, bottom padding, and room for NEI's two buttons. */
    private static int cardHeight(final int band, final int ins, final int outs) {
        final int rows = band + 5 + ROW * (ins + outs) + (ins > 0 ? DIVIDER : 0) + 3;
        // NEI's heart sits 27 px above the widget's foot (card + gap); keep its top below the band.
        return Math.max(rows, band + 30);
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

    /** A rate: whole and grouped from 100 up ("2,048"), below that up to two decimals ("34.74", "32"). */
    static String rate(final double value) {
        if (Math.abs(value) >= 100) return String.format(Locale.US, "%,d", Math.round(value));
        final String digits = String.format(Locale.US, "%.2f", value);
        return digits.contains(".") ? digits.replaceAll("\\.?0+$", "") : digits;
    }

    @Nullable
    private static Flow flow(final Port<?> port) {
        final double perSecond = port.amount();
        final Object value = port.getValue();
        if (value instanceof Energy) return new Flow(null, rate(perSecond / 20) + " EU/t");
        if (value instanceof final FluidStack fluid) {
            final ItemStack shown = com.gtnhplanner.Compat.GREGTECH.isLoaded ? GTHooks.fluidDisplayStack(fluid) : null;
            return shown == null ? null : new Flow(shown, rate(perSecond) + " L/s");
        }
        if (value instanceof final ItemStack item) {
            final ItemStack shown = item.copy();
            shown.stackSize = 1;
            return new Flow(shown, rate(perSecond) + "/s");
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

        private final int chip;
        private final List<String> nameLines;
        private final int band, height;

        Cached(final Entry e) {
            final FontRenderer font = GuiDraw.fontRenderer;
            chip = e.tier()
                .isEmpty() ? 0 : font.getStringWidth(e.tier()) + 4;
            final int room = WIDTH - 3 - chip - 4 - 4;
            final List<String> lines = new ArrayList<>();
            final List<?> wrapped = font.listFormattedStringToWidth(e.name(), Math.max(1, room));
            if (wrapped.size() <= 1) {
                lines.add(e.name());
            } else {
                lines.add(String.valueOf(wrapped.get(0)));
                final StringBuilder rest = new StringBuilder();
                for (int i = 1; i < wrapped.size(); i++) {
                    if (i > 1) rest.append(' ');
                    rest.append(wrapped.get(i));
                }
                lines.add(fit(font, rest.toString(), room));
            }
            nameLines = lines;
            band = lines.size() > 1 ? BAND2 : BAND;
            height = cardHeight(
                band,
                e.inputs()
                    .size(),
                e.outputs()
                    .size());
            int y = firstRow(band);
            for (final Flow f : e.inputs()) {
                if (f.stack() != null) in.add(new PositionedStack(f.stack(), SLOT_X, y + 1, false));
                y += ROW;
            }
            if (!e.inputs()
                .isEmpty()) y += DIVIDER;
            for (final Flow f : e.outputs()) {
                if (f.stack() != null) more.add(new PositionedStack(f.stack(), SLOT_X, y + 1, false));
                y += ROW;
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

    /** Where the first flow row starts: the band, its line, 3 px of air. */
    private static int firstRow(final int band) {
        return band + 5;
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

    /** A plain method, not an override: NEI before per-recipe heights uses the tab's height. */
    public int getRecipeHeight(final int recipe) {
        if (recipe < 0 || recipe >= arecipes.size()) return HEIGHT;
        return ((Cached) arecipes.get(recipe)).height + GAP;
    }

    @Override
    public void drawBackground(final int recipe) {
        if (recipe < 0 || recipe >= arecipes.size()) return;
        final Cached c = (Cached) arecipes.get(recipe);
        // 2 px wider than the widget, so NEI's buttons (to x 166) sit inside the card's border.
        PlannerTabs.panel(0, 0, WIDTH + 2, c.height);
        PlannerTabs.headerBand(1, 1, WIDTH, c.band);
        for (final PositionedStack s : c.getIngredients()) PlannerTabs.slot(s.relx, s.rely);
        for (final PositionedStack s : c.getOtherStacks()) PlannerTabs.slot(s.relx, s.rely);
    }

    private static String fit(final FontRenderer font, final String text, final int room) {
        if (font.getStringWidth(text) <= room) return text;
        return font.trimStringToWidth(text, Math.max(0, room - font.getStringWidth("..."))) + "...";
    }

    /** One flow's line: its rate, then the resource's name quieter beside it. */
    private static void drawRow(final FontRenderer font, final Flow f, final int rowY, final int heartTop) {
        final String rate = f.rate();
        if (rate.isEmpty()) return;
        final int y = rowY + (ROW - 8) / 2;
        // A line above NEI's buttons may run to the card's edge; one beside them stops short.
        final int end = y + 8 <= heartTop ? WIDTH - 4 : TEXT_END;
        final boolean eu = rate.endsWith(" EU/t");
        GuiDraw.drawString(
            rate,
            TEXT_X,
            y,
            eu ? PlannerTabs.theme()
                .eu()
                : PlannerTabs.theme()
                    .rate(),
            false);
        String name = "";
        // An EU row's rate already says EU, and its slot shows the machine, not a resource.
        if (!eu && f.stack() != null) {
            try {
                name = f.stack()
                    .getDisplayName();
            } catch (final RuntimeException ex) {
                name = "";
            }
        }
        final int x = TEXT_X + font.getStringWidth(rate) + 4;
        if (name == null || name.isEmpty() || x >= end) return;
        GuiDraw.drawString(
            fit(font, name, end - x),
            x,
            y,
            PlannerTabs.theme()
                .resource(),
            false);
    }

    @Override
    public void drawExtras(final int recipe) {
        if (recipe < 0 || recipe >= entries.size()) return;
        final Entry e = entries.get(recipe);
        final Cached c = (Cached) arecipes.get(recipe);
        final FontRenderer font = GuiDraw.fontRenderer;
        if (c.chip > 0) {
            final Hyb.Tier t = Hyb.tier(e.tier());
            GuiDraw.drawRect(WIDTH - 3 - c.chip, 2, c.chip, 10, 0xFF000000 | t.bg());
            GuiDraw.drawString(e.tier(), WIDTH - 3 - c.chip + 2, 3, 0xFF000000 | t.text(), false);
        }
        for (int i = 0; i < c.nameLines.size(); i++) {
            GuiDraw.drawString(
                c.nameLines.get(i),
                4,
                3 + 10 * i,
                PlannerTabs.theme()
                    .name(),
                false);
        }
        final int heartTop = getRecipeHeight(recipe) - 27;
        int y = firstRow(c.band);
        for (final Flow f : e.inputs()) {
            drawRow(font, f, y, heartTop);
            y += ROW;
        }
        if (!e.inputs()
            .isEmpty()) {
            GuiDraw.drawRect(
                SLOT_X,
                y + 2,
                TEXT_END - SLOT_X,
                1,
                PlannerTabs.theme()
                    .divider());
            for (int i = 0; i < 4; i++) GuiDraw.drawRect(
                9 + i,
                y + 1 + i,
                7 - 2 * i,
                1,
                PlannerTabs.theme()
                    .arrow());
            y += DIVIDER;
        }
        for (final Flow f : e.outputs()) {
            drawRow(font, f, y, heartTop);
            y += ROW;
        }
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
