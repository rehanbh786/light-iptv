package com.lightiptv.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Free channels from the public iptv-org lists. Each category is its own small
 * file, downloaded only when opened and cached on disk for a day.
 */
public final class Playlist {

    private static final String BASE = "https://iptv-org.github.io/iptv/";
    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;

    public static final String FAVORITES_KEY = "favorites";
    public static final String FEATURED_KEY = "featured";

    /** Channels that open in their official app instead of the built-in player. */
    public static final String APP_PREFIX = "app:";
    public static final String APP_ARY_YOUTUBE = "app:ary-youtube";
    public static final String APP_TAMASHA = "app:tamasha";

    /** A category shown in the sidebar. */
    public static final class Category {
        public final String key;
        public final String title;
        final String path; // null for favorites and featured

        Category(String key, String title, String path) {
            this.key = key;
            this.title = title;
            this.path = path;
        }

        public boolean isFavorites() {
            return FAVORITES_KEY.equals(key);
        }

        public boolean isFeatured() {
            return FEATURED_KEY.equals(key);
        }
    }

    /** Official sources for channels whose free-list links don't work. */
    public static List<Channel> featured() {
        return Arrays.asList(
                new Channel("ARY News  ·  YouTube live", APP_ARY_YOUTUBE,
                        "https://i.imgur.com/R4KtTbJ.jpg", null, null),
                new Channel("PTV Sports  ·  Tamasha", APP_TAMASHA,
                        "https://i.imgur.com/CPm6GHA.png", null, null)
        );
    }

    /** Sidebar order. Adult content is deliberately not included. */
    public static final List<Category> CATEGORIES = Arrays.asList(
            new Category(FEATURED_KEY, "Featured", null),
            new Category(FAVORITES_KEY, "Favorites", null),
            new Category("pk", "Pakistan", "countries/pk.m3u"),
            new Category("news", "News", "categories/news.m3u"),
            new Category("sports", "Sports", "categories/sports.m3u"),
            new Category("kids", "Kids", "categories/kids.m3u"),
            new Category("animation", "Cartoons", "categories/animation.m3u"),
            new Category("movies", "Movies", "categories/movies.m3u"),
            new Category("series", "Series", "categories/series.m3u"),
            new Category("entertainment", "Entertainment", "categories/entertainment.m3u"),
            new Category("music", "Music", "categories/music.m3u"),
            new Category("documentary", "Documentary", "categories/documentary.m3u"),
            new Category("religious", "Religious", "categories/religious.m3u"),
            new Category("education", "Education", "categories/education.m3u"),
            new Category("science", "Science", "categories/science.m3u"),
            new Category("cooking", "Cooking", "categories/cooking.m3u"),
            new Category("lifestyle", "Lifestyle", "categories/lifestyle.m3u"),
            new Category("travel", "Travel", "categories/travel.m3u"),
            new Category("comedy", "Comedy", "categories/comedy.m3u"),
            new Category("family", "Family", "categories/family.m3u"),
            new Category("business", "Business", "categories/business.m3u"),
            new Category("classic", "Classic", "categories/classic.m3u"),
            new Category("culture", "Culture", "categories/culture.m3u"),
            new Category("weather", "Weather", "categories/weather.m3u"),
            new Category("general", "General", "categories/general.m3u")
    );

    private Playlist() {}

    /** Channels of one category, from the disk cache when fresh. */
    public static List<Channel> load(Context ctx, Category cat, boolean forceRefresh) throws IOException {
        File file = new File(ctx.getCacheDir(), "cat_" + cat.key + ".m3u");
        boolean fresh = file.exists() && file.length() > 0
                && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS;
        if (forceRefresh || !fresh) {
            try {
                download(BASE + cat.path, file);
            } catch (IOException e) {
                // Offline: use an older copy if there is one.
                if (!(file.exists() && file.length() > 0)) throw e;
            }
        }
        return parse(file);
    }

    private static void download(String address, File target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "LightIPTV/1.1");
        File tmp = new File(target.getPath() + ".part");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            if (!tmp.renameTo(target)) {
                target.delete();
                if (!tmp.renameTo(target)) throw new IOException("Could not save list");
            }
        } finally {
            conn.disconnect();
            tmp.delete();
        }
    }

    /** Line by line, never the whole file in memory. Duplicate streams are dropped. */
    private static List<Channel> parse(File file) throws IOException {
        Map<String, Channel> byUrl = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            String name = null;
            String logo = null;
            String ua = null;
            String ref = null;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTINF")) {
                    name = parseName(line);
                    logo = attr(line, "tvg-logo");
                    ua = attr(line, "http-user-agent");
                    ref = attr(line, "http-referrer");
                } else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                    ua = line.substring("#EXTVLCOPT:http-user-agent=".length());
                } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                    ref = line.substring("#EXTVLCOPT:http-referrer=".length());
                } else if (!line.startsWith("#") && name != null) {
                    if (!byUrl.containsKey(line)) byUrl.put(line, new Channel(name, line, logo, ua, ref));
                    name = null;
                    logo = null;
                    ua = null;
                    ref = null;
                }
            }
        }
        return new ArrayList<>(byUrl.values());
    }

    /** The display name follows the comma after the attributes. Tags like "(720p)" are removed. */
    private static String parseName(String extinf) {
        int lastQuote = extinf.lastIndexOf('"');
        int comma = extinf.indexOf(',', Math.max(lastQuote, 0));
        String n = comma >= 0 ? extinf.substring(comma + 1).trim() : "";
        n = n.replaceAll("\\s*[\\(\\[](\\d{3,4}p|Geo-blocked|Not 24/7)[\\)\\]]", "").trim();
        return n.isEmpty() ? "Unnamed channel" : n;
    }

    private static String attr(String line, String key) {
        String marker = key + "=\"";
        int start = line.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = line.indexOf('"', start);
        return end > start ? line.substring(start, end) : null;
    }
}
