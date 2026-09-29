# SalviaBrowxer

A fast, private Android browser whose defining feature is a precise media tray: when a page already
exposes a downloadable file, an open HLS playlist, or a blob the page itself can read, the browser
says so quietly and saves it reliably.

Package `com.salvia.salviabrowxer` · `minSdk` 24 · `targetSdk`/`compileSdk` 36 (Android 16) ·
versionName `1.0.0`.

## What it does today

- **Home** — the app opens on a paste field, because a downloader's entry point is the link. A
  copied link is picked up when the app comes to the foreground and fills the field — never
  overwriting something already typed, and never offered twice. A URL that names its own container
  (`…/clip.mp4`, `…/master.m3u8`, `…/stream.mpd`) opens its quality sheet straight away; anything
  else is a page, so it is opened and the media tray finds what that page exposes. There is no
  third behaviour, and no pretending a page link can be turned into a file on its own. Pasted
  Instagram, TikTok, YouTube, Facebook and X hosts (including subdomains and `youtu.be`) always
  open as pages, even when their paths end in a media extension; this does not block media
  actually observed by the page sniffer. Malformed and credential-bearing URLs are not accepted
  by paste classification. The same
  screen lists the transfers in progress with their rate and the time left, and leads to the queue.
- **Browsing** — a real multi-tab browser: up to eight live WebViews, one per tab, and beyond that
  the least recently used tab is hibernated and restored by URL. The chrome is split so every
  control keeps a 48 dp touch target on a 360 dp phone: history in the top bar next to the address
  field and the media pill, and reload / stop, downloads, tabs and the overflow menu along the
  bottom. Home moved into the overflow menu when reload / stop took its slot, so nothing became
  unreachable. A tab switcher with close, new tab and new private tab, private tabs that never
  write history, find in page, share and copy link, five search engines, JavaScript and cookie
  toggles, and a desktop user-agent toggle that shows its state.
- **Library screens** — bookmarks (add from the browser menu, remove in the list) and history open
  in the current tab, with in-list search and per-row delete.
- **Media detection** — four layers feeding one rule set:
  - a **document-start sniffer** hooks `XMLHttpRequest`, `fetch`, `MediaSource.addSourceBuffer` and
    the media elements themselves, and reads the **response** `Content-Type` rather than guessing
    from the URL. That is what finds media on an extension-less CDN path, which is the normal shape
    on social sites. It reports over a `JavascriptInterface` bridge, so a sighting becomes a
    candidate immediately instead of travelling through serialised HTML. In dynamic feeds the
    sniffer re-checks source changes, including nested video elements inserted after load, and
    associates an *already admitted* URL with the visible player. The media tray puts that URL
    first and labels it “On screen”; this is a relevance hint, not a guarantee of “main content”
    (a pre-roll can occupy that same player). SPA address changes drop the previous page's rows.
    Page-provided metadata is included in DOM snapshots even when other media was discovered;
    the previous page's lifetime-wide sniffer log is not reintroduced on a SPA transition.
  - **request interception** admits explicit HTTP(S) media/playlist GET URLs, not blobs or API
    guesses from outgoing headers. Only an absent Range or `bytes=0-` is considered whole-file
    evidence. Fetch/XHR observation rejects partial responses; segments and web manifests are
    excluded from DOM and request candidates too.
  - a **DOM scan** of the loaded page (`media`, `source`, anchors, meta tags, JSON and plain-text
    URLs) for whatever the markup itself states.
  - **blob reassembly** through the bridge, in Binder-safe 480 KiB chunks.

  Every layer takes its notion of "is this media" from one object, `MediaUrlRules` — previously
  there were five copies and they disagreed, most visibly over `manifest`, which made a PWA's
  `site.webmanifest` offer itself as a playlist. Admission is evidence-based, in
  `MediaSniffAdmission`: the type has to come from the server or from a player that actually loaded
  the URL, so HLS segments, ranged fragment reads, web manifests and ordinary page assets never
  reach the tray as candidates that would fail at download time.
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
  in the same queue. Every progress tick also stores the rate the transfer measured and the seconds
  it estimates are left, so a row reads `4.2 MB/s · 0:31 left`. Both are cleared the moment a
  transfer stops, so a paused or finished row never shows a stale speed. Wi-Fi-only mode is off by default: the app browses and downloads on whatever
  connection the phone has, mobile data included, and only holds transfers when the user turns the
  setting on. The settings screen shows the live connection next to that switch. Finished files are
  shareable through `FileProvider` and play in the in-app Media3 player, which reports a file that
  disappeared behind the queue's back and offers to remove the dead row.
- **Library** — downloads in Room, surfaced through the downloads screen; settings in DataStore.
- **Intents** — `VIEW` (http / https) and `SEND` (`text/plain`) are registered: a link handed to the
  app by another app opens in its own tab, and `tel:` / `mailto:` / `intent:` are passed to the
  system instead of being loaded as pages.
- **Language** — English and Persian, both complete. Strings live in `values/` and `values-fa/`,
  `supportsRtl` is on, and `res/xml/locales_config.xml` declares both so Android 13+ shows the app
  in the system per-app language picker. Directional icons are the `AutoMirrored` variants, so the
  back arrow points right in Persian, and nothing is aligned with absolute left/right padding.
- **Typography** — the UI does not use `FontFamily.Default`. Inter ships for Latin locales and
  Vazirmatn for Persian, chosen from the app language; Persian also drops Material's letter
  spacing (which would tear joined Arabic-script words apart) and gets 15% more line height. See
  [`docs/fonts.md`](docs/fonts.md) for both OFL licenses.
- **Accessibility** — TalkBack labels say what a control does rather than what its glyph is named,
  rows that announce both a title and an icon announce it once, toggles and radio rows expose a
  single focus target each, every interactive control is at least 48 dp, and the type scale
  survives a 1.3x font scale because the bars grow instead of clipping.

### Feed detection limitation

On X and other dynamic feeds, the app does not request a hidden media URL, extract a social post
through a site-specific API, or promise that the first/biggest video is the main one. When the page
actually reveals a candidate, the tray can highlight a candidate associated with the on-screen
player; the pill still shows the count of all detected candidates. If X serves only fragments,
keeps the source in a worker, or delays it until a post opens, the pill may not appear in the feed.
A real-device X feed / post / pre-roll comparison is still required before making a coverage claim.

See [`docs/world-class-roadmap.md`](docs/world-class-roadmap.md) for unshipped release gates.

## What it deliberately does not do

SalviaBrowxer is **not** a YouTube, Instagram or TikTok downloader, and it does not defeat
protection:

- no site-specific extractors or signature/token harvesting
- no DRM (Widevine / FairPlay / PlayReady) — the app fails with an honest error
- no AES-128 encrypted HLS key recovery, and no live HLS
- no MPEG-DASH: a `.mpd` is recognised and explained, never downloaded — segmenting a manifest
  needs a parser the app does not have, so it is never offered as a file
- no native FFmpeg binary and no audio/video muxing
- no worker or service-worker sniffing: the injected script runs in the document, so a player that
  fetches its media from a worker stays invisible
- MSE-backed and unproven blob URLs are refused, not offered as downloadable files. Only a
  witnessed real Blob with media evidence can use the existing page-owned blob save path.
  Revoked object URLs are removed from candidates; there is no MSE recording or manifest mapping
- no analytics SDK, no ad SDK, no account system, and no network call other than a page load, a
  media probe, or a download the user started. Detection adds none: it reads response headers the
  page already received rather than probing URLs itself

## Modules

| Module | Contains |
| --- | --- |
| `:app` | screens, view models, services, DI, theme, resources |
| `:core:model` | `MediaCandidate`, `MediaInfo`, `MediaFormat`, `DownloadState`, `Tab`, and the pure detection rules (`MediaUrlRules`, `MediaSniffAdmission`) |
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
