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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Downloads the free iptv-org playlists, caches them on disk for a day,
 * and groups the channels into categories.
 */
public final class Playlist {

    public static final String PAKISTAN = "Pakistan";
    private static final String UNDEFINED = "Undefined";

    private static final String CATEGORY_URL = "https://iptv-org.github.io/iptv/index.category.m3u";
    private static final String PAKISTAN_URL = "https://iptv-org.github.io/iptv/countries/pk.m3u";

    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private Playlist() {}

    /** Category name -> channels. Pakistan comes first, Undefined last, the rest A–Z. */
    public static LinkedHashMap<String, List<Channel>> load(Context ctx, boolean forceRefresh) throws IOException {
        File catFile = new File(ctx.getCacheDir(), "category.m3u");
        File pkFile = new File(ctx.getCacheDir(), "pakistan.m3u");

        refreshIfNeeded(CATEGORY_URL, catFile, forceRefresh);
        try {
            refreshIfNeeded(PAKISTAN_URL, pkFile, forceRefresh);
        } catch (IOException ignored) {
            // The Pakistan list is a bonus; the category list is what matters.
        }

        TreeMap<String, List<Channel>> byCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        parse(catFile, byCategory, null);

        LinkedHashMap<String, List<Channel>> result = new LinkedHashMap<>();
        if (pkFile.exists()) {
            TreeMap<String, List<Channel>> pk = new TreeMap<>();
            parse(pkFile, pk, PAKISTAN);
            List<Channel> pkList = pk.get(PAKISTAN);
            if (pkList != null && !pkList.isEmpty()) result.put(PAKISTAN, pkList);
        }
        List<Channel> undefined = byCategory.remove(UNDEFINED);
        result.putAll(byCategory);
        if (undefined != null) result.put(UNDEFINED, undefined);
        return result;
    }

    private static void refreshIfNeeded(String url, File target, boolean force) throws IOException {
        boolean fresh = target.exists() && target.length() > 0
                && System.currentTimeMillis() - target.lastModified() < MAX_AGE_MS;
        if (fresh && !force) return;
        try {
            download(url, target);
        } catch (IOException e) {
            // Offline or blocked: fall back to an old copy if we have one.
            if (!(target.exists() && target.length() > 0)) throw e;
        }
    }

    private static void download(String address, File target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "LightIPTV/1.0");
        File tmp = new File(target.getPath() + ".part");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " for " + address);
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            if (!tmp.renameTo(target)) {
                target.delete();
                if (!tmp.renameTo(target)) throw new IOException("Could not save playlist");
            }
        } finally {
            conn.disconnect();
            tmp.delete();
        }
    }

    /**
     * Reads the file line by line (never the whole file in memory).
     * If forcedGroup is set, every channel goes into that group.
     */
    private static void parse(File file, Map<String, List<Channel>> out, String forcedGroup) throws IOException {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            String name = null;
            String groups = null;
            String ua = null;
            String ref = null;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTINF")) {
                    name = parseName(line);
                    groups = attr(line, "group-title");
                    ua = attr(line, "http-user-agent");
                    ref = attr(line, "http-referrer");
                } else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                    ua = line.substring("#EXTVLCOPT:http-user-agent=".length());
                } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                    ref = line.substring("#EXTVLCOPT:http-referrer=".length());
                } else if (!line.startsWith("#") && name != null) {
                    Channel ch = new Channel(name, line, ua, ref);
                    if (forcedGroup != null) {
                        add(out, forcedGroup, ch);
                    } else if (groups == null || groups.trim().isEmpty()) {
                        add(out, UNDEFINED, ch);
                    } else {
                        for (String g : groups.split(";")) {
                            String group = g.trim();
                            add(out, group.isEmpty() ? UNDEFINED : group, ch);
                        }
                    }
                    name = null;
                    groups = null;
                    ua = null;
                    ref = null;
                }
            }
        }
    }

    private static void add(Map<String, List<Channel>> out, String group, Channel ch) {
        List<Channel> list = out.get(group);
        if (list == null) {
            list = new ArrayList<>();
            out.put(group, list);
        }
        list.add(ch);
    }

    /** The display name is the text after the comma that follows the attributes. */
    private static String parseName(String extinf) {
        int lastQuote = extinf.lastIndexOf('"');
        int comma = extinf.indexOf(',', Math.max(lastQuote, 0));
        String n = comma >= 0 ? extinf.substring(comma + 1).trim() : "";
        return n.isEmpty() ? "Unnamed channel" : n;
    }

    /** Reads key="value" from an #EXTINF line, or null. */
    private static String attr(String line, String key) {
        String marker = key + "=\"";
        int start = line.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = line.indexOf('"', start);
        return end > start ? line.substring(start, end) : null;
    }
}
