# SalviaBrowxer

A fast, private Android browser whose signature is a precise media tray: when a page already
exposes a downloadable file, an open HLS playlist, or a blob the page itself can read, the browser
says so quietly and saves it reliably.

Package `com.salvia.salviabrowxer` · `minSdk` 24 · `targetSdk`/`compileSdk` 36 (Android 16) ·
versionName `0.9.0` (pre-release).

## What it does today

- **Browsing** — a real multi-tab browser: up to eight live WebViews, one per tab, and beyond that
  the least recently used tab is hibernated and restored by URL. Address bar with back / forward /
  reload / stop, a tab switcher with close, new tab and new private tab, private tabs that never
  write history, find in page, share and copy link, homepage, five search engines, JavaScript and
  cookie toggles, and a desktop user-agent toggle that shows its state.
- **Library screens** — bookmarks (add from the browser menu, remove in the list) and history open
  in the current tab, with in-list search and per-row delete.
- **Media detection** — DOM scan of the loaded page (`media`, `source`, anchors, meta tags, JSON
  and plain-text URLs) plus WebView request interception for HLS, blob and raw media requests the
  page made itself, plus blob reassembly through a `JavascriptInterface` bridge.
- **Media tray** — candidates surface as a quiet pill in the top bar showing only the count; the
  pill opens a tray that lists every candidate with its kind (video, audio, playlist) and container.
  The draggable floating button still exists as an advanced setting, off by default, because a
  button that covers the page should be something the user asked for.
- **Quality sheet** — resolver runs HEAD, falls back to a ranged GET when HEAD is refused, and
  expands an HLS master playlist into one row per variant (resolution, bitrate, size when known).
  The sheet opens immediately from what the page already told us and refines its rows when the
  probe returns, and it never blocks the download button while the probe is still running. A size
  that is not known is reported as unavailable rather than guessed.
- **Downloads** — foreground `dataSync` service, Room-backed queue, pause / cancel / retry,
  direct files resume over HTTP Range from a `.part` file (including after process death),
  non-encrypted VOD HLS playlists are fetched segment by segment and concatenated, blob saves land
  in the same queue. Wi-Fi-only mode pauses and holds transfers off Wi-Fi. Finished files are
  shareable through `FileProvider` and play in the in-app Media3 player, which reports a file that
  disappeared behind the queue's back and offers to remove the dead row.
- **Library** — downloads in Room, surfaced through the downloads screen; settings in DataStore.
- **Intents** — `VIEW` (http / https) and `SEND` (`text/plain`) are registered: a link handed to the
  app by another app opens in its own tab, and `tel:` / `mailto:` / `intent:` are passed to the
  system instead of being loaded as pages.
- **Language** — English today, Persian (`values-fa`) and full RTL are in progress.

## What it deliberately does not do

SalviaBrowxer is **not** a YouTube, Instagram or TikTok downloader, and it does not defeat
protection:

- no site-specific extractors or signature/token harvesting
- no DRM (Widevine / FairPlay / PlayReady) — the app fails with an honest error
- no AES-128 encrypted HLS key recovery, and no live HLS
- no MPEG-DASH: a `.mpd` is recognised and explained, never downloaded — segmenting a manifest
  needs a parser the app does not have, so it is never offered as a file
- no native FFmpeg binary and no audio/video muxing
- no analytics SDK, no ad SDK, no account system, and no network call other than a page load, a
  media probe, or a download the user started

## Modules

| Module | Contains |
| --- | --- |
| `:app` | screens, view models, services, DI, theme, resources |
| `:core:model` | `MediaCandidate`, `MediaInfo`, `MediaFormat`, `DownloadState`, `Tab` |
| `:core:database` | Room database, DAOs, entities, v1→v2 migration |
| `:media:detector` | `DomMediaDetector` behind the `MediaDetector` interface |
| `:media:resolver` | `DirectMediaResolver` (HEAD, ranged GET, HLS variant parse) |
| `:media:downloader` | `DownloadManager` (Range resume) and `HlsDownloader` (VOD, non-encrypted) |

A Gradle module only exists here when it has a public API and a caller outside itself.

## Build

JDK 17 and Android SDK 36 (`platforms;android-36`, `build-tools;36.0.0`). `local.properties` must
point at your SDK; CI gets it from the preinstalled image.

```bash
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # R8 + resource shrinking
./gradlew :app:bundleRelease          # AAB for Play: app/build/outputs/bundle/release/
./gradlew :app:testDebugUnitTest      # unit tests
```

Release signing is read from environment variables only (`SIGNING_KEYSTORE_BASE64`,
`SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`); the keystore is never
committed. Without them the release build is unsigned, which is what CI uploads.

## CI

`.github/workflows/android_ci.yml` builds the debug APK, the release APK and the release AAB, and
uploads all three. `.github/workflows/apk_build.yml` produces the downloadable APK set. CI installs
Android SDK 36 itself.

## Permissions

- `INTERNET` — load pages and downloads
- `ACCESS_NETWORK_STATE` — Wi-Fi-only gating
- `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` — the download service
- `POST_NOTIFICATIONS` — download progress on Android 13+; requested in context, and denying it
  still lets downloads finish

No storage permission is needed: files are written to the app-scoped external downloads directory
(`Android/data/com.salvia.salviabrowxer/files/Downloads`), which the Settings screen states
verbatim, and each finished file is handed to `MediaScannerConnection` so it also appears in the
system downloads UI.

## Privacy

Pages are loaded by the sites you visit, downloads stay on the device, and the app has no account
and no analytics. Backups and device transfer exclude the database, preferences, WebView cookies
and cache. A short privacy policy lives in [`docs/privacy-policy.md`](docs/privacy-policy.md).

## License

GPL-3.0 — see [`LICENSE`](LICENSE). Selling the app is compatible with the GPL as long as
corresponding source is offered to recipients.
