package com.gtnhplanner.ui.popup;

import java.awt.image.BufferedImage;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.data.flowchart.balancer.Severity;
import com.gtnhplanner.ui.BoardSession;
import com.gtnhplanner.ui.PlannerScale;
import com.gtnhplanner.ui.canvas.BoardCanvas;
import com.gtnhplanner.ui.canvas.PlanPicture;
import com.gtnhplanner.ui.card.CardPaint;
import com.gtnhplanner.ui.sound.Sfx;
import com.gtnhplanner.ui.theme.Hyb;

/**
 * The Share key's Screenshot: a picture of the open plan, shown as it will come out. On the left the picture; on the
 * right how much the cards show (detailed, or simple: the zoomed-out look), whether a footer gives the plan's name and
 * what goes in and out, its size, and Copy and Save (the screenshots folder). By default it is the plan and nothing
 * else, with the small GTNH Planner mark every picture carries. A box in the middle of the screen, in the settings'
 * look.
 */
public final class ScreenshotPanel extends Widget<ScreenshotPanel> implements Interactable {

    private static final int TITLE = 22, PAD = 8, ROW = 22, CLOSE = 14;
    /** Room left round the box on screen, the right column's width, and the largest the box gets. */
    private static final int MARGIN = 20, COL_W = 160, MAX_W = 1200, MAX_H = 720;
    private static final int SWITCH_W = 22, SWITCH_H = 12, CHOICE_H = 16, KEY_H = 18, SAVE_W = 58, COPY_W = 46;

    /**
     * The box as big as the screen allows, nearly all of it the picture's well; the right column (the choices, the
     * size, the keys) from {@code col} to {@code right}.
     */
    private final int boxW, boxH, wellW, wellH, col, right;

    private static final int WELL = 0xFF0E0F11, CONTROL = 0xFF2A2C31, CONTROL_HOVER = 0xFF33363C,
        CONTROL_PICKED = 0xFF4A4D55, TRACK = 0xFF3A3C42, SWITCH_ON = 0xFF3E8F72, CYAN = 0xFF22D3EE;

    private static final String[] DETAILS = { "Detailed", "Simple" };
    private static final String[] FORMATS = { "PNG", "GIF" };
    /** The rows of choices: what each is, in the order they stand (a GIF has no transparent background). */
    private static final int FORMAT = 0, CARDS = 1, NAME = 2, FLOWS = 3, TRANSPARENT = 4, NOTES = 5;
    private static final int[] PNG_ROWS = { FORMAT, CARDS, NAME, FLOWS, TRANSPARENT, NOTES },
        GIF_ROWS = { FORMAT, CARDS, NAME, FLOWS, NOTES };

    /** What was chosen last, kept while the game runs. */
    private static PlanPicture.Options chosen = new PlanPicture.Options(
        PlanPicture.Detail.DETAILED,
        false,
        false,
        false,
        true,
        false);

    /** The preview: one texture, kept between openings and replaced as pictures come in. */
    private static final ResourceLocation PREVIEW = new ResourceLocation(GtnhPlanner.MODID, "plan_picture_preview");
    @Nullable
    private static DynamicTexture previewTexture;

    private final BoardSession session;
    private final BoardCanvas canvas;
    /** What the picture shows, and what is being taken now. */
    @Nullable
    private PlanPicture.Options taken, taking;
    @Nullable
    private BufferedImage picture;
    /** The preview's size in GUI units, once this box has one. */
    private float previewW, previewH;
    /** Why there is no picture; what Save or Copy did (good in green, else amber). */
    @Nullable
    private String problem, status;
    private boolean statusGood, keeping;

    private ScreenshotPanel(final BoardSession session, final BoardCanvas canvas) {
        this.session = session;
        this.canvas = canvas;
        boxW = Math.min(MAX_W, PlannerScale.width() - 2 * MARGIN);
        boxH = Math.min(MAX_H, PlannerScale.height() - 2 * MARGIN);
        wellW = boxW - 2 * PAD - 10 - COL_W;
        wellH = boxH - TITLE - 2 * PAD;
        col = PAD + wellW + 10;
        right = boxW - PAD;
        size(boxW, boxH);
    }

    /** Opens the box in the middle of the screen, the picture on its way. */
    public static void open(final ModularPanel parent, final BoardSession session, final BoardCanvas canvas) {
        final ScreenshotPanel shot = new ScreenshotPanel(session, canvas);
        final int w = shot.boxW, h = shot.boxH;
        final Popup popup = new Popup("gtnhplanner_screenshot", w, h) {

            @Override
            public void drawBackground(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
                Hyb.rect(-getArea().x, -getArea().y, PlannerScale.width(), PlannerScale.height(), 0x60000000);
                Hyb.dropShadow(0, 0, w, h);
                CardPaint.surface(w, h);
            }
        };
        popup.child(shot.pos(0, 0));
        Popup.open(parent, popup, (PlannerScale.width() - w) / 2, (PlannerScale.height() - h) / 2);
    }

    // region The picture

    @Override
    public void onUpdate() {
        super.onUpdate();
        // A new choice takes the picture again, once the one on its way is in.
        if (taking == null && !chosen.equals(taken)) take();
    }

    private void take() {
        final PlanPicture.Options o = chosen;
        taking = o;
        PlanPicture.take(canvas, o, image -> {
            final int scale = PlannerScale.current();
            PlanPicture.preview(image, (wellW - 8) * scale, (wellH - 8) * scale, small -> {
                show(small, scale);
                picture = image;
                taken = o;
                taking = null;
                problem = null;
            });
        }, why -> {
            picture = null;
            taken = o;
            taking = null;
            problem = why;
        });
    }

    /** The preview, a pixel of it to a pixel of the screen. */
    private void show(final BufferedImage small, final int scale) {
        if (previewTexture != null) previewTexture.deleteGlTexture();
        previewTexture = new DynamicTexture(small);
        Minecraft.getMinecraft()
            .getTextureManager()
            .loadTexture(PREVIEW, previewTexture);
        previewW = small.getWidth() / (float) scale;
        previewH = small.getHeight() / (float) scale;
    }

    private boolean ready() {
        return picture != null && taking == null && !keeping;
    }

    private void save() {
        if (!ready()) return;
        if (chosen.gif()) {
            saveGif();
            return;
        }
        keeping = true;
        status = null;
        PlanPicture.save(
            picture,
            session.graph()
                .getName(),
            // The plan goes in the picture: it opens as the plan, in the planner and on the website.
            session.pictureJson(),
            file -> {
                keeping = false;
                said("Saved as " + file.getName(), true);
                session.flash(Severity.INFO, "Saved the picture in screenshots: " + file.getName());
            },
            why -> {
                keeping = false;
                said("Couldn't save it: " + why, false);
            });
    }

    private void saveGif() {
        if (!ready()) return;
        keeping = true;
        said("Frame 0 of " + com.gtnhplanner.ui.canvas.GifPicture.FRAMES, true);
        PlanPicture.saveGif(
            canvas,
            chosen,
            session.graph()
                .getName(),
            (frame, of) -> said("Frame " + frame + " of " + of, true),
            file -> {
                keeping = false;
                said("Saved as " + file.getName(), true);
                session.flash(Severity.INFO, "Saved the GIF in screenshots: " + file.getName());
            },
            why -> {
                keeping = false;
                said("Couldn't save it: " + why, false);
            });
    }

    private void copy() {
        if (!ready()) return;
        if (chosen.gif()) {
            said("A GIF can't be copied. Save it instead.", false);
            return;
        }
        keeping = true;
        status = null;
        PlanPicture.copy(picture, session.pictureJson(), () -> {
            keeping = false;
            said("Copied: paste it anywhere", true);
        }, why -> {
            keeping = false;
            said("Couldn't copy it: " + why, false);
        });
    }

    private void said(final String text, final boolean good) {
        status = text;
        statusGood = good;
    }

    // endregion

    // region Where things are (widget-local)

    private static int rowY(final int i) {
        return TITLE + PAD + i * ROW;
    }

    private int[] closeRect() {
        return new int[] { boxW - 4 - CLOSE, (TITLE - CLOSE) / 2, CLOSE, CLOSE };
    }

    private int[] switchRect(final int i) {
        return new int[] { right - SWITCH_W, rowY(i) + (ROW - SWITCH_H) / 2, SWITCH_W, SWITCH_H };
    }

    private static int choiceWidth(final String name) {
        return Hyb.width(name) + 12;
    }

    /** One of a row's choices (its {@code names}), the row {@code row} places down. */
    private int[] choiceRect(final String[] names, final int row, final int i) {
        int w = 1;
        for (final String n : names) w += choiceWidth(n) + 1;
        int x = right - w + 1;
        for (int k = 0; k < i; k++) x += choiceWidth(names[k]) + 1;
        return new int[] { x, rowY(row) + (ROW - CHOICE_H) / 2 + 1, choiceWidth(names[i]), CHOICE_H - 2 };
    }

    private static int[] rowsOf(final PlanPicture.Options o) {
        return o.gif() ? GIF_ROWS : PNG_ROWS;
    }

    private static PlanPicture.Options with(final PlanPicture.Options o, final PlanPicture.Detail detail,
        final boolean name, final boolean flows, final boolean transparent, final boolean annotations,
        final boolean gif) {
        return new PlanPicture.Options(detail, name, flows, transparent && !gif, annotations, gif);
    }

    private int[] saveRect() {
        return new int[] { right - SAVE_W, TITLE + PAD + wellH - KEY_H, SAVE_W, KEY_H };
    }

    private int[] copyRect() {
        return new int[] { right - SAVE_W - 6 - COPY_W, TITLE + PAD + wellH - KEY_H, COPY_W, KEY_H };
    }

    private static boolean in(final int[] r, final int x, final int y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    // endregion

    // region Drawing

    private int mouseX() {
        return getContext().getAbsMouseX() - getArea().x;
    }

    private int mouseY() {
        return getContext().getAbsMouseY() - getArea().y;
    }

    @Override
    public boolean canHover() {
        return true;
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        final boolean hovering = isHovering();
        final int mx = hovering ? mouseX() : -1, my = hovering ? mouseY() : -1;

        Hyb.text("Screenshot", 10, (TITLE - 8) / 2f + 1, Hyb.INK);
        final int[] close = closeRect();
        final boolean closeHot = in(close, mx, my);
        if (closeHot) Hyb.rect(close[0], close[1], close[2], close[3], CONTROL_HOVER);
        cross(close[0] + close[2] / 2f, close[1] + close[3] / 2f, closeHot ? Hyb.INK : Hyb.MUTED);
        Hyb.rect(1, TITLE, boxW - 2, 1, CardPaint.HAIR);

        drawPreview();

        // The choices.
        final PlanPicture.Options o = chosen;
        final int[] rows = rowsOf(o);
        for (int r = 0; r < rows.length; r++) {
            switch (rows[r]) {
                case FORMAT -> drawChoice(r, "Format", FORMATS, o.gif() ? 1 : 0, mx, my);
                case CARDS -> drawChoice(
                    r,
                    "Cards",
                    DETAILS,
                    o.detail()
                        .ordinal(),
                    mx,
                    my);
                case NAME -> drawSwitch(r, "Plan name", o.name(), mx, my);
                case FLOWS -> drawSwitch(r, "Inputs and outputs", o.flows(), mx, my);
                case TRANSPARENT -> drawSwitch(r, "Transparent background", o.transparent(), mx, my);
                default -> drawSwitch(r, "Show annotations", o.annotations(), mx, my);
            }
        }
        final int n = rows.length;
        if (!o.annotations() && !o.gif())
            Hyb.text(Hyb.fit("The embedded plan includes annotations.", right - col), col, rowY(n) - 4, Hyb.MUTED);

        // How big it comes out, and what Save or Copy did.
        final int infoY = rowY(n) + 12;
        Hyb.rect(col, rowY(n) + 6, right - col, 1, CardPaint.HAIR);
        if (picture != null && taking == null)
            Hyb.text(String.format("%,d x %,d pixels", picture.getWidth(), picture.getHeight()), col, infoY, Hyb.MUTED);
        if (status != null)
            Hyb.text(Hyb.fit(status, right - col), col, infoY + 12, statusGood ? Hyb.OK_INK : Hyb.AMBER_INK);
        if (o.gif()) Hyb.text(Hyb.fit("Wires with flow move in the GIF.", right - col), col, infoY + 26, Hyb.MUTED);

        // Copy, and Save in cyan.
        final boolean on = ready();
        final int[] copy = copyRect(), save = saveRect();
        final boolean canCopy = on && !o.gif();
        final boolean copyHot = canCopy && in(copy, mx, my), saveHot = on && in(save, mx, my);
        Hyb.bevel(copy[0], copy[1], copy[2], copy[3], copyHot ? Hyb.KEY_HOVER : Hyb.KEY, Hyb.KEY_HI, Hyb.KEY_LO, 0, 1);
        Hyb.textCentered("Copy", copy[0] + copy[2] / 2f, copy[1] + 5, canCopy ? Hyb.INK : 0xFF5A5C65);
        Hyb.rect(save[0], save[1], save[2], save[3], !on ? 0xFF1E6B78 : saveHot ? 0xFF38E1F5 : CYAN);
        Hyb.rect(save[0], save[1], save[2], 1, 0x66FFFFFF);
        final String saveLabel = keeping ? "..." : "Save";
        com.cleanroommc.modularui.drawable.GuiDraw
            .drawText(saveLabel, save[0] + (save[2] - Hyb.width(saveLabel)) / 2f, save[1] + 5, 1f, 0xFF0B1A1E, false);
    }

    /** The picture in its well, fitted and centred; a word while it is taken, or why there is none. */
    private void drawPreview() {
        final int x = PAD, y = TITLE + PAD;
        Hyb.rect(x, y, wellW, wellH, CardPaint.EDGE);
        Hyb.rect(x + 1, y + 1, wellW - 2, wellH - 2, WELL);
        final float cx = x + wellW / 2f, cy = y + wellH / 2f;
        if (previewTexture != null && previewW > 0 && problem == null) {
            // As big as the well allows: a small picture is drawn larger, so it can be seen.
            final float f = Math.min((wellW - 8) / previewW, (wellH - 8) / previewH);
            final float pw = previewW * f, ph = previewH * f;
            Hyb.texture(PREVIEW, cx - pw / 2f, cy - ph / 2f, pw, ph);
            if (taking != null) Hyb.rect(x + 1, y + 1, wellW - 2, wellH - 2, 0x99000000);
        }
        if (taking != null) Hyb.textCentered("Taking the picture...", cx, cy - 4, Hyb.INK);
        else if (problem != null) {
            Hyb.textCentered("No picture", cx, cy - 10, Hyb.AMBER_INK);
            Hyb.textCentered(Hyb.fit(problem, wellW - 12), cx, cy + 2, Hyb.MUTED);
        }
    }

    /** A row of choices, one picked, in a framed strip. */
    private void drawChoice(final int row, final String text, final String[] names, final int picked, final int mx,
        final int my) {
        label(row, text);
        final int[] first = choiceRect(names, row, 0);
        Hyb.rect(first[0] - 1, first[1] - 1, right - first[0] + 1, CHOICE_H, CardPaint.EDGE);
        for (int i = 0; i < names.length; i++) {
            final int[] r = choiceRect(names, row, i);
            final boolean on = picked == i, hot = in(r, mx, my);
            Hyb.rect(r[0], r[1], r[2], r[3], on ? CONTROL_PICKED : hot ? CONTROL_HOVER : CONTROL);
            Hyb.textCentered(names[i], r[0] + r[2] / 2f, r[1] + (r[3] - 8) / 2f + 1, on || hot ? Hyb.INK : Hyb.MUTED);
        }
    }

    private void label(final int i, final String text) {
        Hyb.text(text, col, rowY(i) + (ROW - 8) / 2f + 1, Hyb.INK);
    }

    private void drawSwitch(final int i, final String text, final boolean on, final int mx, final int my) {
        label(i, text);
        final boolean hot = mx >= col && mx < right && my >= rowY(i) && my < rowY(i) + ROW;
        final int[] c = switchRect(i);
        pill(c[0], c[1], c[2], c[3], on ? SWITCH_ON : hot ? 0xFF44464D : TRACK);
        final int k = c[3] - 4;
        pill(on ? c[0] + c[2] - 2 - k : c[0] + 2, c[1] + 2, k, k, on ? Hyb.INK : 0xFF9A9CA4);
    }

    /** A box with its corners off, a pixel's rounding. */
    private static void pill(final int x, final int y, final int w, final int h, final int colour) {
        Hyb.rect(x + 1, y, w - 2, h, colour);
        Hyb.rect(x, y + 1, 1, h - 2, colour);
        Hyb.rect(x + w - 1, y + 1, 1, h - 2, colour);
    }

    private static void cross(final float cx, final float cy, final int colour) {
        for (int d = -3; d <= 3; d++) {
            Hyb.rect(cx + d - 0.5f, cy + d - 0.5f, 1, 1, colour);
            Hyb.rect(cx + d - 0.5f, cy - d - 0.5f, 1, 1, colour);
        }
    }

    // endregion

    // region Input

    @Override
    public Result onMousePressed(final int mouseButton) {
        if (mouseButton != 0) return Result.SUCCESS;
        final int mx = mouseX(), my = mouseY();
        final PlanPicture.Options o = chosen;
        if (in(closeRect(), mx, my)) {
            if (getPanel() != null) getPanel().closeIfOpen();
            Sfx.CLOSE.play();
        } else if (in(saveRect(), mx, my) && ready()) {
            Hyb.click();
            save();
        } else if (in(copyRect(), mx, my) && ready()) {
            Hyb.click();
            copy();
        } else if (mx >= col && mx < right && my >= rowY(0) && my < rowY(rowsOf(o).length)) {
            final int row = (my - rowY(0)) / ROW;
            switch (rowsOf(o)[row]) {
                case FORMAT -> {
                    for (int i = 0; i < FORMATS.length; i++)
                        if (in(choiceRect(FORMATS, row, i), mx, my) && (i == 1) != o.gif()) {
                            chosen = with(o, o.detail(), o.name(), o.flows(), o.transparent(), o.annotations(), i == 1);
                            Sfx.TICK.play(i > 0 ? 1.12f : 0.9f);
                        }
                }
                case CARDS -> {
                    for (int i = 0; i < DETAILS.length; i++)
                        if (in(choiceRect(DETAILS, row, i), mx, my) && i != o.detail()
                            .ordinal()) {
                                chosen = with(
                                    o,
                                    PlanPicture.Detail.values()[i],
                                    o.name(),
                                    o.flows(),
                                    o.transparent(),
                                    o.annotations(),
                                    o.gif());
                                Sfx.TICK.play(i > 0 ? 1.12f : 0.9f);
                            }
                }
                case NAME -> {
                    chosen = with(o, o.detail(), !o.name(), o.flows(), o.transparent(), o.annotations(), o.gif());
                    (chosen.name() ? Sfx.TOGGLE_ON : Sfx.TOGGLE_OFF).play();
                }
                case FLOWS -> {
                    chosen = with(o, o.detail(), o.name(), !o.flows(), o.transparent(), o.annotations(), o.gif());
                    (chosen.flows() ? Sfx.TOGGLE_ON : Sfx.TOGGLE_OFF).play();
                }
                case TRANSPARENT -> {
                    chosen = with(o, o.detail(), o.name(), o.flows(), !o.transparent(), o.annotations(), o.gif());
                    (chosen.transparent() ? Sfx.TOGGLE_ON : Sfx.TOGGLE_OFF).play();
                }
                default -> {
                    chosen = with(o, o.detail(), o.name(), o.flows(), o.transparent(), !o.annotations(), o.gif());
                    (chosen.annotations() ? Sfx.TOGGLE_ON : Sfx.TOGGLE_OFF).play();
                }
            }
            status = null;
        }
        return Result.SUCCESS;
    }

    // endregion
}
