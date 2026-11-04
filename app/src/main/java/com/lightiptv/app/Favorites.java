package com.lightiptv.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Favorite channels, stored in full so they open without loading any category. */
public final class Favorites {
    private static final String PREFS = "favorites_v2";
    private static final String KEY = "items";
    private static final String SEP = "\u001F";

    private Favorites() {}

    public static List<Channel> list(Context ctx) {
        String raw = prefs(ctx).getString(KEY, "");
        List<Channel> out = new ArrayList<>();
        if (raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            String[] f = line.split(SEP, -1);
            if (f.length < 5 || f[1].isEmpty()) continue;
            out.add(new Channel(f[0], f[1], nullIfEmpty(f[2]), nullIfEmpty(f[3]), nullIfEmpty(f[4])));
        }
        return out;
    }

    public static Set<String> urls(Context ctx) {
        Set<String> s = new HashSet<>();
        for (Channel c : list(ctx)) s.add(c.url);
        return s;
    }

    /** Returns true if the channel is now a favorite. */
    public static boolean toggle(Context ctx, Channel ch) {
        List<Channel> items = list(ctx);
        boolean removed = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).url.equals(ch.url)) {
                items.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) items.add(ch);

        StringBuilder sb = new StringBuilder();
        for (Channel c : items) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(clean(c.name)).append(SEP)
              .append(clean(c.url)).append(SEP)
              .append(clean(c.logo)).append(SEP)
              .append(clean(c.userAgent)).append(SEP)
              .append(clean(c.referrer));
        }
        prefs(ctx).edit().putString(KEY, sb.toString()).apply();
        return !removed;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace("\n", " ").replace(SEP, " ");
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
