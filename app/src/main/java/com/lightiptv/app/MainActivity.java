package com.lightiptv.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sidebar of categories, grid of channel cards. Built in code, no XML layouts. */
public class MainActivity extends Activity {

    // Palette
    static final int BG = Color.parseColor("#0B0F17");
    static final int SIDEBAR = Color.parseColor("#111723");
    static final int CARD = Color.parseColor("#1A2130");
    static final int CARD_FOCUS = Color.parseColor("#26324A");
    static final int ACCENT = Color.parseColor("#4D8DFF");
    static final int TEXT = Color.parseColor("#EEF2F8");
    static final int MUTED = Color.parseColor("#8A94A8");

    private static final int UPDATE_ROW = -1; // marker for the "Update lists" row

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Map<String, List<Channel>> loaded = new HashMap<>();

    private int activeIndex = 1;   // Pakistan
    private int pendingIndex = 1;
    private int generation;
    private boolean focusGridWhenLoaded;
    private List<Channel> shown = new ArrayList<>();
    private Set<String> favoriteUrls;

    private ListView sidebar;
    private SidebarAdapter sidebarAdapter;
    private GridView grid;
    private CardAdapter cardAdapter;
    private TextView title;
    private TextView subtitle;
    private View highlighted;

    private final Runnable loadPending = this::openPendingCategory;

    private void openPendingCategory() {
        if (pendingIndex != activeIndex) openCategory(pendingIndex, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        favoriteUrls = Favorites.urls(this);
        buildUi();
        sidebar.setSelection(activeIndex);
        openCategory(activeIndex, false);
        sidebar.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Favorites may have changed in the player.
        favoriteUrls = Favorites.urls(this);
        if (Playlist.CATEGORIES.get(activeIndex).isFavorites()) {
            showChannels(Favorites.list(this));
        } else if (cardAdapter != null) {
            cardAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        loader.shutdownNow();
    }

    // ------------------------------------------------------------------ layout

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setClipChildren(false);

        // Sidebar
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(SIDEBAR);
        side.setPadding(dp(20), dp(28), dp(14), dp(20));

        TextView brand = text(24, TEXT, true);
        brand.setText("Light IPTV");
        side.addView(brand);
        TextView tagline = text(13, MUTED, false);
        tagline.setText("Free channels");
        tagline.setPadding(0, dp(2), 0, dp(18));
        side.addView(tagline);

        sidebarAdapter = new SidebarAdapter();
        sidebar = new ListView(this);
        sidebar.setAdapter(sidebarAdapter);
        sidebar.setSelector(rounded(Color.parseColor("#22304A"), 0, 0));
        sidebar.setDivider(new ColorDrawable(Color.TRANSPARENT));
        sidebar.setDividerHeight(dp(2));
        sidebar.setVerticalScrollBarEnabled(false);
        sidebar.setCacheColorHint(Color.TRANSPARENT);
        side.addView(sidebar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(side, new LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.MATCH_PARENT));

        // Content
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(36), dp(28), dp(36), dp(16));
        content.setClipChildren(false);
        content.setClipToPadding(false);

        title = text(30, TEXT, true);
        content.addView(title);
        subtitle = text(14, MUTED, false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        content.addView(subtitle);

        cardAdapter = new CardAdapter();
        grid = new GridView(this);
        grid.setAdapter(cardAdapter);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(176));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(16));
        grid.setVerticalSpacing(dp(18));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setDrawSelectorOnTop(false);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        grid.setVerticalScrollBarEnabled(false);
        grid.setCacheColorHint(Color.TRANSPARENT);
        content.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = text(13, MUTED, false);
        hint.setText("OK  play     Hold OK  add or remove favorite     Back  categories");
        hint.setPadding(0, dp(10), 0, 0);
        content.addView(hint);

        root.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        setContentView(root);

        // Sidebar: moving the highlight opens the category after a short pause.
        sidebar.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (sidebarAdapter.categoryAt(position) == UPDATE_ROW) return;
                pendingIndex = position;
                ui.removeCallbacks(loadPending);
                ui.postDelayed(loadPending, 250);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        sidebar.setOnItemClickListener((parent, view, position, id) -> {
            ui.removeCallbacks(loadPending);
            if (sidebarAdapter.categoryAt(position) == UPDATE_ROW) {
                loaded.clear();
                Toast.makeText(this, "Updating channel lists…", Toast.LENGTH_SHORT).show();
                openCategory(activeIndex, true);
                return;
            }
            focusGridWhenLoaded = true;
            if (position == activeIndex && !shown.isEmpty()) {
                focusGrid();
            } else {
                openCategory(position, false);
            }
        });

        // Grid: highlight follows the remote.
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                highlight(grid.hasFocus() ? view : null);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                highlight(null);
            }
        });
        grid.setOnFocusChangeListener((v, hasFocus) -> highlight(hasFocus ? grid.getSelectedView() : null));
        grid.setOnItemClickListener((parent, view, position, id) -> play(position));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= shown.size()) return true;
            Channel ch = shown.get(position);
            boolean fav = Favorites.toggle(this, ch);
            favoriteUrls = Favorites.urls(this);
            Toast.makeText(this, (fav ? "Added to favorites: " : "Removed from favorites: ") + ch.name,
                    Toast.LENGTH_SHORT).show();
            sidebarAdapter.notifyDataSetChanged();
            if (Playlist.CATEGORIES.get(activeIndex).isFavorites()) {
                showChannels(Favorites.list(this));
            } else {
                cardAdapter.notifyDataSetChanged();
            }
            return true;
        });
    }

    @Override
    public void onBackPressed() {
        if (grid.hasFocus()) {
            sidebar.requestFocus();
            sidebar.setSelection(activeIndex);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ data

    private void openCategory(int index, boolean force) {
        if (index < 0 || index >= Playlist.CATEGORIES.size()) return;
        Playlist.Category cat = Playlist.CATEGORIES.get(index);
        activeIndex = index;
        sidebarAdapter.notifyDataSetChanged();
        title.setText(cat.title);
        final int gen = ++generation;

        if (cat.isFavorites()) {
            showChannels(Favorites.list(this));
            return;
        }
        List<Channel> cached = force ? null : loaded.get(cat.key);
        if (cached != null) {
            showChannels(cached);
            return;
        }

        subtitle.setText("Loading…");
        showChannels(new ArrayList<>(), false);
        final Context app = getApplicationContext();
        loader.execute(() -> {
            List<Channel> result;
            String error = null;
            try {
                result = Playlist.load(app, cat, force);
            } catch (Exception e) {
                result = new ArrayList<>();
                error = e.getMessage();
            }
            final List<Channel> list = result;
            final String err = error;
            ui.post(() -> {
                if (gen != generation || isFinishing()) return; // user moved on
                if (err == null) loaded.put(cat.key, list);
                showChannels(list);
                if (err != null) {
                    subtitle.setText("Could not load this list. Check the internet and choose “Update lists”.");
                }
            });
        });
    }

    private void showChannels(List<Channel> list) {
        showChannels(list, true);
    }

    private void showChannels(List<Channel> list, boolean updateSubtitle) {
        shown = list;
        highlighted = null;
        cardAdapter.notifyDataSetChanged();
        grid.setSelection(0);
        if (updateSubtitle) {
            Playlist.Category cat = Playlist.CATEGORIES.get(activeIndex);
            if (list.isEmpty()) {
                subtitle.setText(cat.isFavorites()
                        ? "No favorites yet. Hold OK on any channel to add it here."
                        : "No channels in this category right now.");
            } else {
                subtitle.setText(list.size() + " channels  ·  some free streams may be offline");
            }
        }
        if (focusGridWhenLoaded && !list.isEmpty()) {
            focusGridWhenLoaded = false;
            focusGrid();
        }
    }

    private void focusGrid() {
        grid.requestFocus();
        grid.setSelection(0);
        grid.post(() -> highlight(grid.getSelectedView()));
    }

    private void play(int position) {
        if (position < 0 || position >= shown.size()) return;
        Session.channels = new ArrayList<>(shown);
        Session.categoryName = Playlist.CATEGORIES.get(activeIndex).title;
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_INDEX, position);
        startActivity(i);
    }

    // ------------------------------------------------------------------ focus effect

    private void highlight(View card) {
        if (highlighted != null && highlighted != card) styleCard(highlighted, false, true);
        if (card != null) styleCard(card, true, true);
        highlighted = card;
    }

    private void styleCard(View card, boolean focused, boolean animate) {
        float scale = focused ? 1.08f : 1f;
        if (animate) {
            card.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        } else {
            card.animate().cancel();
            card.setScaleX(scale);
            card.setScaleY(scale);
        }
        card.setBackground(focused
                ? rounded(CARD_FOCUS, dp(3), ACCENT)
                : rounded(CARD, 0, 0));
        card.setElevation(focused ? dp(8) : 0);
    }

    // ------------------------------------------------------------------ helpers

    private GradientDrawable rounded(int fill, int strokePx, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(12));
        if (strokePx > 0) d.setStroke(strokePx, strokeColor);
        return d;
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ adapters

    private final class SidebarAdapter extends BaseAdapter {
        private final int size = Playlist.CATEGORIES.size();

        /** Category index for a row, or UPDATE_ROW for the last row. */
        int categoryAt(int position) {
            return position < size ? position : UPDATE_ROW;
        }

        @Override
        public int getCount() {
            return size + 1;
        }

        @Override
        public Object getItem(int position) {
            return position < size ? Playlist.CATEGORIES.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = convertView instanceof TextView ? (TextView) convertView : null;
            if (row == null) {
                row = new TextView(MainActivity.this);
                row.setPadding(dp(14), dp(10), dp(10), dp(10));
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                row.setSingleLine(true);
                row.setEllipsize(TextUtils.TruncateAt.END);
            }
            if (position >= size) {
                row.setText("Update lists");
                row.setTextColor(MUTED);
                row.setTypeface(Typeface.DEFAULT);
                return row;
            }
            Playlist.Category cat = Playlist.CATEGORIES.get(position);
            String label = cat.title;
            if (cat.isFavorites()) {
                int n = favoriteUrls == null ? 0 : favoriteUrls.size();
                label = "★  Favorites" + (n > 0 ? "  " + n : "");
            }
            row.setText(label);
            boolean active = position == activeIndex;
            row.setTextColor(active ? ACCENT : TEXT);
            row.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            return row;
        }
    }

    private static final class CardHolder {
        ImageView logo;
        TextView initials;
        TextView name;
    }

    private final class CardAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout card;
            CardHolder h;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof CardHolder) {
                card = (LinearLayout) convertView;
                h = (CardHolder) card.getTag();
            } else {
                card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(10), dp(10), dp(10), dp(10));

                android.widget.FrameLayout art = new android.widget.FrameLayout(MainActivity.this);
                GradientDrawable artBg = new GradientDrawable();
                artBg.setColor(Color.parseColor("#0F141E"));
                artBg.setCornerRadius(dp(8));
                art.setBackground(artBg);

                h = new CardHolder();
                h.initials = text(28, MUTED, true);
                h.initials.setGravity(Gravity.CENTER);
                art.addView(h.initials, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                h.logo = new ImageView(MainActivity.this);
                h.logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                h.logo.setPadding(dp(12), dp(10), dp(12), dp(10));
                art.addView(h.logo, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                card.addView(art, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(86)));

                h.name = text(15, TEXT, false);
                h.name.setMaxLines(2);
                h.name.setEllipsize(TextUtils.TruncateAt.END);
                h.name.setGravity(Gravity.CENTER_HORIZONTAL);
                h.name.setPadding(0, dp(8), 0, 0);
                h.name.setMinLines(2);
                card.addView(h.name);

                card.setTag(h);
            }

            Channel ch = shown.get(position);
            boolean fav = favoriteUrls != null && favoriteUrls.contains(ch.url);
            h.name.setText(fav ? "★ " + ch.name : ch.name);
            h.initials.setText(initials(ch.name));
            ImageLoader.get(MainActivity.this).load(ch.logo, h.logo);

            boolean focused = grid.hasFocus() && position == grid.getSelectedItemPosition();
            styleCard(card, focused, false);
            if (focused) highlighted = card;
            return card;
        }
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (part.isEmpty()) continue;
            char c = part.charAt(0);
            if (Character.isLetterOrDigit(c)) sb.append(Character.toUpperCase(c));
            if (sb.length() == 2) break;
        }
        return sb.length() == 0 ? "TV" : sb.toString();
    }
}
