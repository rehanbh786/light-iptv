package com.lightiptv.app;

/** One playable channel from an M3U playlist. */
public final class Channel {
    public final String name;
    public final String url;
    public final String logo;      // may be null
    public final String userAgent; // may be null
    public final String referrer;  // may be null

    public Channel(String name, String url, String logo, String userAgent, String referrer) {
        this.name = name;
        this.url = url;
        this.logo = logo;
        this.userAgent = userAgent;
        this.referrer = referrer;
    }
}
