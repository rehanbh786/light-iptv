package com.lightiptv.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-screen player. Up/Down (or Channel +/-) switch channels, OK shows the
 * channel name, holding OK toggles favorite, Back returns to the list.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {

    public static final String EXTRA_INDEX = "index";
    private static final String STATE_INDEX = "state_index";
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideInfo = this::hideInfoNow;

    private List<Channel> channels;
    private int index;
    private boolean forcedHls;

    private ExoPlayer player;
    private FrameLayout frame;
    private SurfaceView surface;
    private TextView info;
    private TextView message;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        channels = Session.channels;
        if (channels == null || channels.isEmpty()) {
            // App was restarted by Android and lost the list; go back to it.
            finish();
            return;
        }
        index = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_INDEX, 0)
                : getIntent().getIntExtra(EXTRA_INDEX, 0);
        index = Math.max(0, Math.min(index, channels.size() - 1));

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
    }

    private void buildUi() {
        frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        surface = new SurfaceView(this);
        frame.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        message = new TextView(this);
        message.setTextColor(Color.WHITE);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        message.setGravity(Gravity.CENTER);
        message.setBackgroundColor(Color.parseColor("#AA000000"));
        message.setPadding(dp(24), dp(16), dp(24), dp(16));
        frame.addView(message, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        android.graphics.drawable.GradientDrawable infoBg = new android.graphics.drawable.GradientDrawable();
        infoBg.setColor(Color.parseColor("#E60B0F17"));
        infoBg.setCornerRadius(dp(12));
        infoBg.setStroke(dp(2), Color.parseColor("#4D8DFF"));
        info.setBackground(infoBg);
        info.setPadding(dp(20), dp(12), dp(20), dp(12));
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        infoLp.setMargins(dp(32), 0, 0, dp(32));
        frame.addView(info, infoLp);

        setContentView(frame);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (channels == null) return;
        createPlayer();
        playChannel(index, false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        ui.removeCallbacksAndMessages(null);
        releasePlayer();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_INDEX, index);
    }

    // ---------------------------------------------------------------- player

    private void createPlayer() {
        // Small buffers: this has to run on boxes with 1 GB of RAM.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(8_000, 20_000, 1_500, 3_000)
                .build();
        player = new ExoPlayer.Builder(this).setLoadControl(loadControl).build();
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    showMessage("Loading…");
                } else if (state == Player.STATE_READY) {
                    message.setVisibility(View.GONE);
                } else if (state == Player.STATE_ENDED) {
                    showMessage("This stream ended.\n▲ ▼ to switch channel");
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                // Many streams are HLS without ".m3u8" in the address: retry once as HLS.
                if (!forcedHls) {
                    playChannel(index, true);
                    return;
                }
                showMessage("This channel is not available right now.\n▲ ▼ to try another channel");
            }

            @Override
            public void onVideoSizeChanged(VideoSize size) {
                fitVideo(size);
            }
        });
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void playChannel(int newIndex, boolean asHls) {
        if (player == null) return;
        index = newIndex;
        forcedHls = asHls;
        Channel ch = channels.get(index);

        Map<String, String> headers = new HashMap<>();
        if (ch.referrer != null && !ch.referrer.isEmpty()) headers.put("Referer", ch.referrer);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ch.userAgent != null && !ch.userAgent.isEmpty() ? ch.userAgent : DEFAULT_UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000)
                .setDefaultRequestProperties(headers);

        MediaItem.Builder item = new MediaItem.Builder().setUri(ch.url);
        if (asHls || ch.url.toLowerCase(Locale.ROOT).contains(".m3u8")) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8);
            forcedHls = true; // already HLS; don't retry the same thing
        }
        MediaSource source = new DefaultMediaSourceFactory(http).createMediaSource(item.build());

        // Reset to full screen until the new stream reports its size.
        surface.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        player.setMediaSource(source);
        player.prepare();
        player.setPlayWhenReady(true);
        showMessage("Loading…");
        showInfo();
    }

    private void switchBy(int delta) {
        int n = channels.size();
        int next = ((index + delta) % n + n) % n;
        playChannel(next, false);
    }

    /** Keeps the picture's shape instead of stretching it. */
    private void fitVideo(VideoSize size) {
        if (size.width == 0 || size.height == 0) return;
        int fw = frame.getWidth();
        int fh = frame.getHeight();
        if (fw == 0 || fh == 0) return;
        float videoAspect = size.width * size.pixelWidthHeightRatio / size.height;
        float frameAspect = (float) fw / fh;
        int w;
        int h;
        if (videoAspect > frameAspect) {
            w = fw;
            h = Math.round(fw / videoAspect);
        } else {
            h = fh;
            w = Math.round(fh * videoAspect);
        }
        surface.setLayoutParams(new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
    }

    // ---------------------------------------------------------------- overlays

    private void showInfo() {
        Channel ch = channels.get(index);
        String category = Session.categoryName != null ? Session.categoryName : "";
        info.setText((index + 1) + " / " + channels.size() + "   " + ch.name
                + "\n" + category + "   ·   ▲ ▼ change channel   ·   hold OK: favorite");
        info.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideInfo);
        ui.postDelayed(hideInfo, 4_000);
    }

    private void hideInfoNow() {
        if (info != null) info.setVisibility(View.GONE);
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- remote keys

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                switchBy(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                switchBy(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                event.startTracking(); // lets a long press reach onKeyLongPress
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                && event.isTracking() && !event.isCanceled()) {
            showInfo();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            Channel ch = channels.get(index);
            boolean fav = Favorites.toggle(this, ch);
            Toast.makeText(this, (fav ? "Added to favorites: " : "Removed from favorites: ") + ch.name,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }
}
