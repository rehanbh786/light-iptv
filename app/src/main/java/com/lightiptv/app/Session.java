package com.lightiptv.app;

import java.util.List;

/** Hands the current channel list to the player (too big for an Intent). */
final class Session {
    static List<Channel> channels;
    static String categoryName;

    private Session() {}
}
