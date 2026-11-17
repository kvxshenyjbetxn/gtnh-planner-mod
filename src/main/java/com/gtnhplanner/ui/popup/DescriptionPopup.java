package com.gtnhplanner.ui.popup;

import java.util.function.Consumer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnhplanner.ui.theme.Hyb;

/**
 * A titled box for a plan's description: many lines, up to {@link #MAX} characters. Enter saves and closes, Shift+Enter
 * adds a line, Esc closes without saving, a click outside saves.
 */
public final class DescriptionPopup extends Popup {

    /** The most a description holds, in characters. */
    public static final int MAX = 2000;

    private static final int W = 260, H = 112;

    private final TextArea area;
    private final Consumer<String> commit;
    private boolean done;
    private boolean focused;

    private DescriptionPopup(final String current, final Consumer<String> commit) {
        super("gtnhplanner_description", W, H);
        this.commit = commit;
        area = new TextArea("Describe this plan").maxLength(MAX)
            .submitOnEnter(this::save, this::cancel);
        area.text(current)
            .caretAtEnd();
        child(
            new TextWidget<>(IKey.str("Plan description")).color(Hyb.MUTED)
                .shadow(true)
                .pos(6, 6)
                .size(W - 12, 10));
        child(
            area.pos(6, 20)
                .size(W - 12, 72));
        child(
            new TextWidget<>(IKey.str("Enter saves, Shift+Enter adds a line")).color(Hyb.MUTED)
                .pos(6, H - 14)
                .size(W - 12, 10));
        area.onUpdateListener(w -> {
            if (!focused && w.isValid()) {
                focused = true;
                w.getContext()
                    .focus(w);
            }
        }, true);
    }

    /** The box, filled with {@code current}; {@code commit} hears the text when it is saved. */
    public static Popup create(final String current, final Consumer<String> commit) {
        return new DescriptionPopup(current, commit);
    }

    private void save() {
        if (done) return;
        done = true;
        commit.accept(area.text());
        closeIfOpen();
    }

    private void cancel() {
        done = true;
        closeIfOpen();
    }

    @Override
    public boolean closeOnOutOfBoundsClick() {
        save();
        return super.closeOnOutOfBoundsClick();
    }
}
