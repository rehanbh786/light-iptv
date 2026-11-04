package com.lightiptv.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny logo loader for low-memory boxes: logos are shrunk to card size,
 * kept in a small memory cache, and saved on disk so they load once.
 */
final class ImageLoader {

    private static final int TARGET_PX = 160;
    private static final int MAX_BYTES = 1024 * 1024;

    private static ImageLoader instance;

    private final LruCache<String, Bitmap> memory;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final File dir;

    static synchronized ImageLoader get(Context ctx) {
        if (instance == null) instance = new ImageLoader(ctx.getApplicationContext());
        return instance;
    }

    private ImageLoader(Context ctx) {
        dir = new File(ctx.getCacheDir(), "logos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 16); // 1/16 of the app heap
        memory = new LruCache<String, Bitmap>(kb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    void load(final String url, final ImageView view) {
        view.setImageDrawable(null);
        if (url == null || url.isEmpty()) {
            view.setTag(null);
            return;
        }
        Bitmap cached = memory.get(url);
        if (cached != null) {
            view.setTag(url);
            view.setImageBitmap(cached);
            return;
        }
        view.setTag(url);
        pool.execute(() -> {
            if (!url.equals(view.getTag())) return; // view was reused while waiting
            final Bitmap bmp = fetch(url);
            if (bmp == null) return;
            memory.put(url, bmp);
            ui.post(() -> {
                if (url.equals(view.getTag())) view.setImageBitmap(bmp);
            });
        });
    }

    private Bitmap fetch(String url) {
        File file = new File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length());
        try {
            if (!file.exists() || file.length() == 0) download(url, file);
            return decode(file);
        } catch (Exception | OutOfMemoryError e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        }
    }

    private static void download(String address, File target) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "LightIPTV/1.1");
        File tmp = new File(target.getPath() + ".part");
        try {
            if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
            int total = 0;
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_BYTES) throw new Exception("Logo too large");
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(target)) throw new Exception("Could not save logo");
        } finally {
            conn.disconnect();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static Bitmap decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= TARGET_PX && bounds.outHeight / (sample * 2) >= TARGET_PX / 2) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getPath(), opts);
    }
}
