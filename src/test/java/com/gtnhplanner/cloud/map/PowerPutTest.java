package com.gtnhplanner.cloud.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnhplanner.cloud.sync.LiveOp;

class PowerPutTest {

    @Test
    void aNewPowerRecipeGoesWithItsReadingsButAPatchNeverCarriesThem() {
        final JsonObject before = new JsonParser().parse("{\"recipes\":[]}")
            .getAsJsonObject();
        final JsonObject after = new JsonParser()
            .parse("{\"recipes\":[{\"id\":\"r\",\"power\":{\"sourceId\":\"x\",\"euPerTick\":32,\"stats\":[]}}]}")
            .getAsJsonObject();
        final List<LiveOp> ops = CloudPlans.diff(before, after);
        assertEquals(1, ops.size());
        assertEquals(
            LiveOp.Kind.PUT,
            ops.get(0)
                .o());
        assertTrue(
            ops.get(0)
                .v()
                .getAsJsonObject("power")
                .has("euPerTick"));
        final JsonObject later = new JsonParser()
            .parse("{\"recipes\":[{\"id\":\"r\",\"power\":{\"sourceId\":\"x\",\"euPerTick\":64,\"stats\":[]}}]}")
            .getAsJsonObject();
        assertTrue(
            CloudPlans.diff(after, later)
                .isEmpty());
    }
}
