package com.gtnhplanner.nei;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * Finds a recipe of one handler instance from its saved id, without building every recipe's id on each lookup: ids of
 * other handlers are turned away by name, the rest looked up in a map built once.
 */
public final class HandlerIndex {

    private final TemplateRecipeHandler handler;
    @Nullable
    private final String handlerName;
    @Nullable
    private Map<Recipe.RecipeId, Integer> index;

    public HandlerIndex(final TemplateRecipeHandler handler) {
        this.handler = handler;
        this.handlerName = nameOf(handler);
    }

    public TemplateRecipeHandler handler() {
        return handler;
    }

    @Nullable
    private static String nameOf(final TemplateRecipeHandler handler) {
        try {
            final HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
            return info == null ? null : info.getHandlerName();
        } catch (final RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    public RecipeHandlerRef find(final Recipe.RecipeId id) {
        if (handlerName != null) {
            final String idName = id.getHandlerName();
            if (idName != null && !handlerName.equals(idName)) return null;
        }
        try {
            if (index == null) {
                final Map<Recipe.RecipeId, Integer> built = new HashMap<>();
                for (int i = 0; i < handler.arecipes.size(); i++) {
                    built.putIfAbsent(Recipe.RecipeId.of(handler, i), i);
                }
                index = built;
            }
            final Integer i = index.get(id);
            return i == null ? null : RecipeHandlerRef.of(handler, i);
        } catch (final RuntimeException e) {
            for (int i = 0; i < handler.arecipes.size(); i++) {
                if (Recipe.RecipeId.of(handler, i)
                    .equals(id)) return RecipeHandlerRef.of(handler, i);
            }
            return null;
        }
    }
}
