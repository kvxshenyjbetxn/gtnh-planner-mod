package com.gtnhplanner.nei;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import javax.annotation.Nullable;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;

/**
 * A card's recipe found again from its saved id. NEI finds most by searching for the recipe's result; a recipe page
 * that has no result in that sense (CropsNH's Crops page) registers a lookup of its own here, tried when NEI's finds
 * nothing.
 */
public final class RecipeRefs {

    private static final List<Function<Recipe.RecipeId, RecipeHandlerRef>> FALLBACKS = new CopyOnWriteArrayList<>();

    private static final Map<Recipe.RecipeId, Optional<RecipeHandlerRef>> CACHE = new ConcurrentHashMap<>();

    private RecipeRefs() {}

    public static void addFallback(final Function<Recipe.RecipeId, RecipeHandlerRef> lookup) {
        FALLBACKS.add(lookup);
    }

    @Nullable
    public static RecipeHandlerRef of(@Nullable final Recipe.RecipeId id) {
        if (id == null) return null;
        final Optional<RecipeHandlerRef> cached = CACHE.get(id);
        if (cached != null) return cached.orElse(null);
        final RecipeHandlerRef found = resolve(id);
        if (found != null) CACHE.put(id, Optional.of(found));
        else if (ItemList.loadFinished) CACHE.put(id, Optional.empty());
        return found;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    @Nullable
    private static RecipeHandlerRef resolve(final Recipe.RecipeId id) {
        final RecipeHandlerRef ref = RecipeHandlerRef.of(id);
        if (ref != null) return ref;
        for (final Function<Recipe.RecipeId, RecipeHandlerRef> f : FALLBACKS) {
            try {
                final RecipeHandlerRef found = f.apply(id);
                if (found != null) return found;
            } catch (final RuntimeException | LinkageError ignored) {}
        }
        return null;
    }
}
