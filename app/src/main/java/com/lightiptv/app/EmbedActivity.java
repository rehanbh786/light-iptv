package com.lightiptv.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Plays a channel's official YouTube live stream full screen inside the app,
 * using YouTube's own embedded player (the supported way for apps to show it).
 */
public class EmbedActivity extends Activity {

    public static final String EXTRA_YOUTUBE_CHANNEL = "youtube_channel";
    public static final String EXTRA_TITLE = "title";

    /** YouTube checks the embedding origin; this identifies the app. */
    private static final String ORIGIN = "https://com.lightiptv.app";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView banner;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String channel = getIntent().getStringExtra(EXTRA_YOUTUBE_CHANNEL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (channel == null || !channel.matches("[A-Za-z0-9_-]+")) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setFocusable(false); // the remote talks to this screen, not the page
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        banner = new TextView(this);
        banner.setTextColor(Color.WHITE);
        banner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        banner.setPadding(pad + pad / 2, pad, pad + pad / 2, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E60B0F17"));
        bg.setCornerRadius(pad);
        bg.setStroke(Math.round(2 * getResources().getDisplayMetrics().density), Color.parseColor("#4D8DFF"));
        banner.setBackground(bg);
        banner.setText((title != null ? title : "Live") + "   ·   OK  play / pause   ·   Back  exit");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        lp.setMargins(pad * 2, 0, 0, pad * 2);
        root.addView(banner, lp);

        setContentView(root);
        web.loadDataWithBaseURL(ORIGIN + "/", page(channel), "text/html", "utf-8", null);
        hideBannerLater();
    }

    private static String page(String channel) {
        String src = "https://www.youtube.com/embed/live_stream?channel=" + channel
                + "&autoplay=1&controls=0&rel=0&playsinline=1&enablejsapi=1&origin=" + ORIGIN;
        return "<!doctype html><html><head>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}"
                + "iframe{position:fixed;top:0;left:0;width:100%;height:100%;border:0}</style>"
                + "</head><body>"
                + "<iframe id='p' src='" + src + "' allow='autoplay; encrypted-media; fullscreen'"
                + " allowfullscreen referrerpolicy='strict-origin-when-cross-origin'></iframe>"
                + "<script src='https://www.youtube.com/iframe_api'></script>"
                + "<script>"
                + "var player;"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('p',"
                + "{events:{onReady:function(e){e.target.playVideo();}}});}"
                + "function togglePlay(){if(!player||!player.getPlayerState)return;"
                + "if(player.getPlayerState()==1){player.pauseVideo();}else{player.playVideo();}}"
                + "</script></body></html>";
    }

    private void hideBannerLater() {
        banner.setVisibility(View.VISIBLE);
        ui.removeCallbacksAndMessages(null);
        ui.postDelayed(() -> banner.setVisibility(View.GONE), 5_000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (web != null) web.evaluateJavascript("togglePlay()", null);
            hideBannerLater();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            hideBannerLater();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) {
            web.loadUrl("about:blank"); // stop the audio right away
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
