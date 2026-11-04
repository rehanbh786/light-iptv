package com.lightiptv.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Favorite channels, stored by stream URL. */
public final class Favorites {
    private static final String PREFS = "favorites";
    private static final String KEY = "urls";

    private Favorites() {}

    public static Set<String> get(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Copy: the set returned by SharedPreferences must not be modified.
        return new HashSet<>(p.getStringSet(KEY, new HashSet<>()));
    }

    /** Returns true if the channel is now a favorite. */
    public static boolean toggle(Context ctx, String url) {
        Set<String> set = get(ctx);
        boolean nowFavorite;
        if (set.contains(url)) {
            set.remove(url);
            nowFavorite = false;
        } else {
            set.add(url);
            nowFavorite = true;
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply();
        return nowFavorite;
    }
}
