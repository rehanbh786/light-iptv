# Light IPTV

A small Android TV app for free, publicly listed channels, grouped by category.

- Channels come from the community-maintained [iptv-org](https://github.com/iptv-org/iptv) lists:
  `index.category.m3u` (all categories) and `countries/pk.m3u` (shown as a "Pakistan" category).
- Lists are cached for 24 hours. Choose **Update channel list** to refresh.
- Some streams are offline or geo-blocked at any given time. That is the source, not the app.
- No accounts, no ads, no analytics.

## Remote

| Screen  | Key                      | Action                          |
|---------|--------------------------|---------------------------------|
| Lists   | OK                       | Open category / play channel    |
| Lists   | Hold OK on a channel     | Add or remove favorite          |
| Lists   | Back                     | Return to categories            |
| Player  | Up / Down, Channel +/-   | Previous / next channel         |
| Player  | OK                       | Show channel name               |
| Player  | Hold OK                  | Add or remove favorite          |
| Player  | Back                     | Return to the list              |

## Build

Push this folder to a GitHub repository. The workflow in `.github/workflows/build.yml`
builds `app-debug.apk` and attaches it to the run under **Actions → latest run → Artifacts**.

Install on the box:

```
adb install -r app-debug.apk
```

Tech: plain Java, AndroidX Media3 ExoPlayer, minSdk 21, no native libraries
(one APK runs on 32-bit and 64-bit boxes). Small player buffers for 1 GB RAM devices.
