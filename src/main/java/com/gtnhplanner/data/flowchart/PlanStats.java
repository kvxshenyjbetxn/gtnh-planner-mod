package com.gtnhplanner.data.flowchart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * What the Library's My plans shelf sorts and filters a plan by, kept with its slot so no plan has to be opened or
 * solved to be listed (the website's design stats): its power draw (EU/t), its highest machine tier (an index into the
 * voltage tiers, -1 for none), how many machines it builds, and the resources it makes and takes (what it sends out of
 * and brings into the plan). Worked out from the overview's totals each time a plan solves; local only, never in plan
 * codes or exports. Pure.
 */
public record PlanStats(double euT, int tierIndex, int machines, List<String> makes, List<String> takes) {

    /** Below this an amount is solver noise, not a resource worth listing. */
    private static final double EPS = 1e-6;

    /**
     * The stats from the overview's numbers: {@code machineCounts} one per machine line (how many it runs; a part of a
     * machine is a whole one to build), {@code outputs} and {@code inputs} each resource key to its amount.
     */
    public static PlanStats of(final double euT, final int tierIndex, final double[] machineCounts,
        final Map<String, Double> outputs, final Map<String, Double> inputs) {
        int machines = 0;
        for (final double m : machineCounts) if (m > 1e-9) machines += (int) Math.ceil(m - 1e-9);
        return new PlanStats(euT, tierIndex, machines, keys(outputs), keys(inputs));
    }

    private static List<String> keys(final Map<String, Double> amounts) {
        final Set<String> out = new TreeSet<>();
        for (final Map.Entry<String, Double> e : amounts.entrySet()) if (e.getValue() > EPS) out.add(e.getKey());
        return new ArrayList<>(out);
    }

    /**
     * Whether a plan passes the filters (the website's): {@code maxTier} the highest tier index allowed (negative for
     * any), {@code maxEuT} the most EU/t allowed (negative for any), and every key of {@code makes} and {@code takes}
     * present. A plan with no stats passes only when nothing filters by them.
     */
    public static boolean passes(@Nullable final PlanStats s, final int maxTier, final double maxEuT,
        final Collection<String> makes, final Collection<String> takes) {
        if (maxTier < 0 && maxEuT < 0 && makes.isEmpty() && takes.isEmpty()) return true;
        if (s == null) return false;
        if (maxEuT >= 0 && s.euT > maxEuT) return false;
        if (maxTier >= 0 && s.tierIndex > maxTier) return false;
        return s.makes.containsAll(makes) && s.takes.containsAll(takes);
    }

    /** The sort key for Highest tier (descending); a plan with no stats is -1. */
    public static int tierOf(@Nullable final PlanStats s) {
        return s == null ? -1 : s.tierIndex;
    }

    /** The sort key for Highest power (descending); a plan with no stats is -1. */
    public static double powerOf(@Nullable final PlanStats s) {
        return s == null ? -1 : s.euT;
    }

    /** The sort key for Lowest power (ascending); a plan with no stats goes last. */
    public static double lowPowerOf(@Nullable final PlanStats s) {
        return s == null ? Double.POSITIVE_INFINITY : s.euT;
    }

    /** The sort key for Most machines (descending); a plan with no stats is -1. */
    public static int machinesOf(@Nullable final PlanStats s) {
        return s == null ? -1 : s.machines;
    }

    public JsonObject toJson() {
        final JsonObject o = new JsonObject();
        o.addProperty("euT", euT);
        o.addProperty("tier", tierIndex);
        o.addProperty("machines", machines);
        o.add("makes", array(makes));
        o.add("takes", array(takes));
        return o;
    }

    /** Stats read back; null when {@code e} is not stats. */
    @Nullable
    public static PlanStats fromJson(@Nullable final JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        try {
            final JsonObject o = e.getAsJsonObject();
            return new PlanStats(
                o.get("euT")
                    .getAsDouble(),
                o.get("tier")
                    .getAsInt(),
                o.get("machines")
                    .getAsInt(),
                strings(o.get("makes")),
                strings(o.get("takes")));
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    private static JsonArray array(final List<String> list) {
        final JsonArray a = new JsonArray();
        for (final String s : list) a.add(new com.google.gson.JsonPrimitive(s));
        return a;
    }

    private static List<String> strings(@Nullable final JsonElement e) {
        final List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) for (final JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        return out;
    }
}
