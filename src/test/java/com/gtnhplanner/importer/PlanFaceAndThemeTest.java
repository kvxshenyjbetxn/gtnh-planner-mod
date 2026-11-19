package com.gtnhplanner.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnhplanner.data.flowchart.Graph;
import com.gtnhplanner.data.flowchart.Note;
import com.gtnhplanner.library.PlanExport;

/**
 * What a plan keeps of its icon and of an annotation's fill theme through the importer and back out through the
 * export.
 */
class PlanFaceAndThemeTest {

    private static final String PLAN = "high-amperage-reactor.json";

    private static FakeGame game() {
        final FakeGame game = new FakeGame();
        game.routed = true;
        game.generators = true;
        return game;
    }

    private static JsonObject plan(final JsonElement icon, final JsonObject annotation) {
        final JsonObject root = new JsonParser().parse(FakeGame.fixture(PLAN))
            .getAsJsonObject();
        if (icon != null) root.add("icon", icon);
        if (annotation != null) {
            final JsonArray list = new JsonArray();
            list.add(annotation);
            root.add("annotations", list);
        }
        return root;
    }

    private static JsonObject icon(final String kind, final String id) {
        final JsonObject o = new JsonObject();
        if (kind != null) o.addProperty("kind", kind);
        if (id != null) o.addProperty("resourceId", id);
        return o;
    }

    private static Graph open(final JsonObject plan, final FakeGame game) {
        return FfImport.importFromText(plan.toString(), game, game)
            .graph();
    }

    private static JsonObject export(final Graph g, final FakeGame game) {
        return PlanExport.project(g, g.getName(), LibraryRoundTripTest.world(game, g));
    }

    private static JsonObject box(final String theme) {
        final JsonObject a = new JsonObject();
        a.addProperty("id", "box1");
        a.addProperty("kind", "box");
        final JsonObject at = new JsonObject();
        at.addProperty("x", 0);
        at.addProperty("y", 0);
        a.add("position", at);
        final JsonObject size = new JsonObject();
        size.addProperty("width", 200);
        size.addProperty("height", 120);
        a.add("size", size);
        final JsonObject style = new JsonObject();
        style.addProperty("fill", "tint");
        if (theme != null) style.addProperty("fillTheme", theme);
        a.add("style", style);
        return a;
    }

    @Test
    void aPlansIconComesInAndGoesBackOut() {
        final FakeGame game = game();
        final Graph g = open(plan(icon("fluid", "hydrogen"), null), game);
        assertEquals("fluid:hydrogen", g.getIcon());
        final JsonObject out = export(g, game).getAsJsonObject("icon");
        assertNotNull(out);
        assertEquals(
            "fluid",
            out.get("kind")
                .getAsString());
        assertEquals(
            "hydrogen",
            out.get("resourceId")
                .getAsString());
    }

    @Test
    void aMalformedIconIsLeftOutWithoutFailingTheLoad() {
        final FakeGame game = game();
        assertNull(open(plan(icon("fluid", null), null), game).getIcon(), "no resource id");
        assertNull(open(plan(icon("gizmo", "hydrogen"), null), game).getIcon(), "an unknown kind");
        assertNull(open(plan(icon("item", ""), null), game).getIcon(), "an empty id");
        assertNull(open(plan(icon("item", "x".repeat(201)), null), game).getIcon(), "an id too long");
        assertNull(open(plan(new JsonParser().parse("\"hydrogen\""), null), game).getIcon(), "not an object");
        assertNull(open(plan(null, null), game).getIcon(), "none at all");
    }

    @Test
    void anAnnotationsFillThemeRoundTrips() {
        final FakeGame game = game();
        final Graph g = open(plan(null, box("blueprint")), game);
        final Note note = g.getNotes()
            .iterator()
            .next();
        assertEquals("blueprint", note.getFillTheme());
        final JsonObject style = export(g, game).getAsJsonArray("annotations")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("style");
        assertEquals(
            "blueprint",
            style.get("fillTheme")
                .getAsString());
        assertEquals(
            "blueprint",
            note.copy()
                .getFillTheme());
    }

    @Test
    void anOverlongOrMissingFillThemeIsLeftOut() {
        final FakeGame game = game();
        final Graph g = open(plan(null, box("t".repeat(33))), game);
        assertNull(
            g.getNotes()
                .iterator()
                .next()
                .getFillTheme());
        final Graph none = open(plan(null, box(null)), game);
        assertFalse(
            export(none, game).getAsJsonArray("annotations")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("style")
                .has("fillTheme"));
    }
}
