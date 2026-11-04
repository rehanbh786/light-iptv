package com.lightiptv.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Categories on the left, channels on the right. Built in code, no XML layouts. */
public class MainActivity extends Activity {

    private static final String FAVORITES = "★ Favorites";
    private static final String REFRESH = "⟳ Update channel list";

    private static final int BG = Color.parseColor("#10141F");
    private static final int PANEL = Color.parseColor("#182033");
    private static final int ACCENT = Color.parseColor("#2F6FDB");
    private static final int MUTED = Color.parseColor("#9AA4B8");

    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinkedHashMap<String, List<Channel>> data = new LinkedHashMap<>();
    private final List<String> categoryKeys = new ArrayList<>();
    private List<Channel> shown = new ArrayList<>();
    private String shownCategory;
    private boolean loading;

    private TextView status;
    private TextView channelHeader;
    private ListView categoryList;
    private ListView channelList;
    private TextAdapter categoryAdapter;
    private TextAdapter channelAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadData(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Favorites may have changed in the player; refresh the labels.
        if (!data.isEmpty()) rebuildCategories();
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(32), dp(20), dp(32), dp(16));

        TextView title = text(26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setText("Light IPTV");
        root.addView(title);

        status = text(15, MUTED);
        root.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rowLp.topMargin = dp(12);
        root.addView(row, rowLp);

        // Left: categories
        LinearLayout left = column();
        TextView catHeader = text(16, MUTED);
        catHeader.setText("Categories");
        left.addView(catHeader);
        categoryAdapter = new TextAdapter(this);
        categoryList = list(categoryAdapter);
        left.addView(categoryList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        leftLp.rightMargin = dp(16);
        row.addView(left, leftLp);

        // Right: channels
        LinearLayout right = column();
        channelHeader = text(16, MUTED);
        right.addView(channelHeader);
        channelAdapter = new TextAdapter(this);
        channelList = list(channelAdapter);
        right.addView(channelList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        row.addView(right, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 2f));

        TextView hint = text(14, MUTED);
        hint.setText("OK: open / play   ·   Hold OK on a channel: add or remove favorite   ·   Back: return");
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(8);
        root.addView(hint, hintLp);

        setContentView(root);

        categoryList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showCategory(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        categoryList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= categoryKeys.size()) return;
            if (REFRESH.equals(categoryKeys.get(position))) {
                loadData(true);
                return;
            }
            showCategory(position);
            if (!shown.isEmpty()) {
                channelList.requestFocus();
                channelList.setSelection(0);
            }
        });

        channelList.setOnItemClickListener((parent, view, position, id) -> play(position));
        channelList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= shown.size()) return true;
            Channel ch = shown.get(position);
            boolean fav = Favorites.toggle(this, ch.url);
            Toast.makeText(this, (fav ? "Added to favorites: " : "Removed from favorites: ") + ch.name,
                    Toast.LENGTH_SHORT).show();
            rebuildCategories();
            return true;
        });

        categoryList.requestFocus();
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(PANEL);
        col.setPadding(dp(12), dp(10), dp(12), dp(10));
        return col;
    }

    private ListView list(TextAdapter adapter) {
        ListView lv = new ListView(this);
        lv.setAdapter(adapter);
        lv.setSelector(new ColorDrawable(ACCENT));
        lv.setDivider(new ColorDrawable(Color.TRANSPARENT));
        lv.setDividerHeight(dp(2));
        lv.setItemsCanFocus(false);
        lv.setFocusable(true);
        lv.setCacheColorHint(Color.TRANSPARENT);
        return lv;
    }

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (channelList.hasFocus()) {
            categoryList.requestFocus();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- data

    private void loadData(boolean force) {
        if (loading) return;
        loading = true;
        status.setText(force ? "Updating channel list…" : "Loading channels…");
        final Context app = getApplicationContext();
        new Thread(() -> {
            try {
                LinkedHashMap<String, List<Channel>> d = Playlist.load(app, force);
                ui.post(() -> {
                    loading = false;
                    if (isFinishing()) return;
                    data = d;
                    rebuildCategories();
                    status.setText(countChannels() + " free channels in " + d.size()
                            + " categories  ·  from the public iptv-org lists, some may be offline");
                });
            } catch (Exception e) {
                ui.post(() -> {
                    loading = false;
                    if (isFinishing()) return;
                    status.setText("Could not load channels (" + e.getMessage()
                            + "). Check the internet, then choose “Update channel list”.");
                    rebuildCategories();
                });
            }
        }, "playlist-loader").start();
    }

    private int countChannels() {
        Set<String> urls = new HashSet<>();
        for (List<Channel> l : data.values()) for (Channel c : l) urls.add(c.url);
        return urls.size();
    }

    private List<Channel> favoritesList() {
        Set<String> favs = Favorites.get(this);
        LinkedHashMap<String, Channel> unique = new LinkedHashMap<>();
        if (!favs.isEmpty()) {
            for (List<Channel> l : data.values()) {
                for (Channel c : l) {
                    if (favs.contains(c.url) && !unique.containsKey(c.url)) unique.put(c.url, c);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private void rebuildCategories() {
        String previous = shownCategory;
        categoryKeys.clear();
        List<String> labels = new ArrayList<>();

        List<Channel> favs = favoritesList();
        if (!favs.isEmpty()) {
            categoryKeys.add(FAVORITES);
            labels.add(FAVORITES + "  (" + favs.size() + ")");
        }
        for (Map.Entry<String, List<Channel>> e : data.entrySet()) {
            categoryKeys.add(e.getKey());
            labels.add(e.getKey() + "  (" + e.getValue().size() + ")");
        }
        categoryKeys.add(REFRESH);
        labels.add(REFRESH);
        categoryAdapter.setItems(labels);

        int pos = previous == null ? -1 : categoryKeys.indexOf(previous);
        if (pos < 0) pos = 0;
        categoryList.setSelection(pos);
        showCategory(pos);
    }

    private void showCategory(int position) {
        if (position < 0 || position >= categoryKeys.size()) return;
        String key = categoryKeys.get(position);
        if (REFRESH.equals(key)) return; // keep showing the last real category

        List<Channel> list = FAVORITES.equals(key) ? favoritesList() : data.get(key);
        if (list == null) list = new ArrayList<>();
        shown = list;
        shownCategory = key;
        channelHeader.setText(key + "  ·  " + list.size() + " channels");

        Set<String> favs = Favorites.get(this);
        List<String> names = new ArrayList<>(list.size());
        for (Channel c : list) names.add(favs.contains(c.url) ? "★ " + c.name : c.name);
        channelAdapter.setItems(names);
    }

    private void play(int position) {
        if (position < 0 || position >= shown.size()) return;
        Session.channels = new ArrayList<>(shown);
        Session.categoryName = shownCategory;
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_INDEX, position);
        startActivity(i);
    }

    // ---------------------------------------------------------------- adapter

    /** Plain text rows sized for a TV. */
    private static final class TextAdapter extends BaseAdapter {
        private final Context ctx;
        private List<String> items = new ArrayList<>();

        TextAdapter(Context ctx) {
            this.ctx = ctx;
        }

        void setItems(List<String> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(ctx);
                float d = ctx.getResources().getDisplayMetrics().density;
                int padH = Math.round(12 * d);
                int padV = Math.round(9 * d);
                tv.setPadding(padH, padV, padH, padV);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
                tv.setTextColor(Color.WHITE);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
            }
            tv.setText(items.get(position));
            return tv;
        }
    }
}
