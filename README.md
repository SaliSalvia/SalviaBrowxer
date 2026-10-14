# SalviaBrowxer

A fast, private Android browser whose defining feature is a precise media tray: when a page already
exposes a downloadable file, an open HLS playlist, or a blob the page itself can read, the browser
says so quietly and saves it reliably.

Package `com.salvia.salviabrowxer` · `minSdk` 24 · `targetSdk`/`compileSdk` 36 (Android 16) ·
versionName `1.0.0`.

## What it does today

- **Entry point** — the app is a browser, so it launches on the browser: the address field, the
  media pill and the page are the first thing on screen. The paste screen ("Start screen") sits one
  tap away in the overflow menu for when a link is already in hand, and behaves as described here.
  A copied link is picked up when the app comes to the foreground and fills the field — never
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
  write history and are never restored (they are **not** a separate cookie jar - the WebView profile
  is shared, and the app says so in the tab switcher instead of implying isolation), find in page, share and copy link, five search engines, JavaScript and cookie
  toggles, and a desktop user-agent toggle that shows its state.
- **Ad and tracker blocking (opt-in, off by default)** — a short, hand-written list of well-known
  ad and tracker hosts, matched **label by label** on the request's host: `doubleclick.net` covers
  `static.doubleclick.net` and never `notdoubleclick.net`. Blocked requests are answered with an
  empty 200 before anything else looks at them, so a blocked ad cannot appear in the media tray as
  something to download. Media, playlists and segments are **never** blocked, whatever host serves
  them, and neither are loopback/private addresses or the page's own host — the app is a downloader
  first, and a blocker that breaks the video would be a worse bug than an ad. The switch is off
  until the user turns it on, the menu has **Off for this site** for a page that misbehaves, the
  settings row shows how many requests were blocked and how many sites are exempt, and the whole
  decision is local: no list is fetched from anywhere, and nothing about what was blocked leaves the
  device.
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
    excluded from DOM and request candidates too. The same rule runs over a page's **service worker**
    requests as well (`ServiceWorkerClientCompat`) — observed, never answered: the app reads the URL,
    the method and the `Range` header, returns nothing, and no body is ever read. A player that
    fetches its media inside a worker is no longer invisible.
  - a **DOM scan** of the loaded page (`media`, `source`, lazy `data-*` player attributes,
    `application/ld+json` `contentUrl`, Open Graph / Twitter meta tags, anchors, and JSON or
    plain-text URLs) for whatever the markup itself states. A `<source type="video/mp4">` or a
    lazy `data-video-src` may name only an extension-less URL; the declared media type is then the
    evidence, and the same declared type now travels across the sniffing bridge instead of being
    visible to the DOM scanner and dropped by the sniffer.
  - **blob reassembly** through the bridge, in Binder-safe 480 KiB chunks, bounded to a few
    transfers at a time and only for a frame the app has verified as part of the page on screen.
  - **frame attribution**: every frame announces itself once over the recording port — the one
    channel where WebKit also reports the frame's real origin — so a report can be attributed to a
    frame the document actually contains, and a frame cannot claim to be another site. Media inside a
    same-origin or cross-origin `iframe` is therefore visible in the tray (a frame of the current
    document is a legitimate source), while a sighting claiming an origin the document does not
    contain lands nowhere. On a WebView too old to carry the message port the app keeps its previous
    behaviour instead of silently losing detections.
  - **playback recording** for what none of the above can reach: a page that plays with
    `SourceBuffer.appendBuffer` never turns its media into a URL, so the injected hook copies each
    appended segment and posts it to a `WebMessageListener` — as an `ArrayBuffer`, not base64 — and
    the app writes it to a cache file. The bytes stay on the device and only a recording the user
    started is kept. Protected streams are refused with an explanation, not recorded into a file
    that will not open.

  Every layer takes its notion of "is this media" from one object, `MediaUrlRules` — previously
  there were five copies and they disagreed, most visibly over `manifest`, which made a PWA's
  `site.webmanifest` offer itself as a playlist. That one list now also carries the wider set of
  containers plain file-serving sites still use (`ogv`, `mpg`/`mpeg`, `wmv`, `f4v`, `m2ts`/`mts`,
  `vob`, `asf`, `3g2`, `opus`, `oga`, `mka`, `aif`/`aiff`, `m4b`, `amr`, `ac3`), so every layer
  recognises them without a second copy drifting. Admission is evidence-based, in
  `MediaSniffAdmission`: the type has to come from the server or from a player that actually loaded
  the URL, so HLS segments, ranged fragment reads, web manifests and ordinary page assets never
  reach the tray as candidates that would fail at download time.
- **Media tray** — candidates surface as a quiet pill in the top bar showing only the count; the
  pill opens a tray that lists every candidate with its kind (video, audio, playlist) and container.
  The draggable floating button still exists as an advanced setting, off by default, because a
  button that covers the page should be something the user asked for.
- **Quality sheet** — resolver runs HEAD, falls back to a ranged GET when HEAD is refused, and
  fetches the playlist *in parallel with that probe* whenever the URL path already names a manifest,
  so the rows appear sooner instead of after two serial round trips. It expands an HLS master
  playlist into one row per variant (resolution, bitrate, size when known).
  A clear, **static, single-period MPEG-DASH** manifest is parsed the same way: each `Representation`
  becomes a row, and a video rendition is paired with the best audio into a single “1080p + audio”
  option that the app downloads as two streams and muxes itself. The sheet opens immediately from
  what the page already told us and refines its rows when the probe returns, and it never blocks the
  download button while the probe is still running. A size that is not known is reported as
  unavailable rather than guessed.
- **Downloads** — foreground `dataSync` service, Room-backed queue, pause / cancel / retry,
  direct files resume over HTTP Range from a `.part` file (including after process death) and, when
  the server states the file's length and accepts ranges, are fetched over several connections at
  once and joined byte for byte (settings: *Several connections per download*, on by default). A
  server that cannot serve ranges is detected with a one-byte probe, not assumed, and the file then
  comes down on one connection; every part is resumable, so pausing or being killed mid-transfer
  keeps what was already fetched, and nothing is joined until every part has its full length,
  VOD HLS playlists are fetched several segments at a time (bounded parallelism) and
  concatenated, DASH representations are fetched the same way and resume too, blob saves land in
  the same queue. An **AES-128 encrypted** HLS playlist is decrypted with the key the manifest names,
  `EXT-X-BYTERANGE` segments are honoured, and a paused or process-killed segmented transfer
  resumes from the parts it already staged. A segmented transfer that fails on a transient network
  error is **retried a bounded number of times with backoff**, and the retry resumes too: the staged
  segments are kept, and a DASH rendition that already finished is not fetched again, so only the
  missing segments are requested. A segmented
  transfer is then **remuxed into a plain MP4 with `MediaMuxer`** (no re-encode), and a DASH
  video+audio pair is **muxed into one file** — so the saved file plays in any gallery or player,
  not only in this app. The finished file's duration and a frame are read locally for the library
  rows, and (by default) it is also **published to the system media store** so galleries and other
  apps can open it. A finished download that is not in the store yet also offers an explicit
  **Save to gallery** action, which is what covers the two cases the automatic step cannot: the
  export setting was off, or the device is running Android 9 or below where the publish needs a
  storage permission — that action asks for it, and refuses with a message if the user declines. Every progress tick also stores the rate the transfer measured and the seconds
  it estimates are left, so a row reads `4.2 MB/s · 0:31 left`. Both are cleared the moment a
  transfer stops, so a paused or finished row never shows a stale speed. Wi-Fi-only mode is off by default: the app browses and downloads on whatever
  connection the phone has, mobile data included, and only holds transfers when the user turns the
  setting on. The settings screen shows the live connection next to that switch. Finished files are
  shareable through `FileProvider` and play in the in-app Media3 player, which reports a file that
  disappeared behind the queue's back and offers to remove the dead row.
- **A folder with a password, a folder on the card, and tabs that come back** — a password-protected
  folder keeps its verifier (PBKDF2, 120,000 iterations, a fresh salt) in a preference file encrypted
  with a keystore-backed key; the password itself is never stored. Moving a finished download into it
  hides it from the list, removes the copy that had been published to the gallery and forgets it in
  the row, and the folder relocks when the app is closed. Files stay in the app's own private
  directory: the lock decides who *sees* them, it is not encryption of the bytes, and the settings row
  states which of the two the device actually gave us (a keystore that cannot be opened falls back to
  private preferences, and says so). A **folder you choose** through the system picker
  (`ACTION_OPEN_DOCUMENT_TREE`, permission kept across restarts — the only way an app may write to a
  card) receives a *copy* of every finished download: the app's own file is what pause, resume, the
  player and delete operate on, a copy already made is the user's to keep even if the file is later
  locked, and a folder that has gone away costs the copy and nothing else. **Tabs come back** after
  the process is killed: the list is written a moment after every change, private tabs are never
  written, only `http(s)` pages are restored, and it happens once per process rather than once per
  screen.
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
- no DRM (Widevine / FairPlay / PlayReady) — a manifest carrying `ContentProtection` is refused
  with an honest error, as is a `SAMPLE-AES` HLS playlist. Standard **AES-128** HLS — the non-DRM
  scheme whose key the playlist plainly names — is decrypted, not refused
- no live HLS and no dynamic/multi-period DASH — there is no end to them
- **no transcoding**: there is no native FFmpeg binary. The app only *remuxes* (copies already
  encoded tracks into an MP4 container) and *muxes* a separate video and audio track together; it
  never re-encodes, so quality is the original. This is what makes a segmented HLS/DASH download
  and a “1080p + audio” adaptive pair produce a normal playable file
- MSE-backed and unproven blob URLs are not offered as *files* — there is no URL for them to
  download from. Only a witnessed real Blob with media evidence can use the page-owned blob save
  path; revoked object URLs are removed from candidates. What an MSE-backed player offers instead is
  **recording**: the sheet records the playback on explicit request and writes exactly what the
  session played, at the quality the player chose — not the full stream, and not a different
  rendition fetched from the server. A recording can only be started for the page's own player: a
  cross-origin `iframe` is out of reach for the same reason `evaluateJavascript` cannot enter one, so
  a player embedded that way is detected but not recordable
- recording needs a WebView with `WEB_MESSAGE_LISTENER` and `WEB_MESSAGE_ARRAY_BUFFER` (WebView 116+
  for the binary channel); on an older WebView the sheet says so instead of offering the button. A
  stream that turns out to be protected (an `encrypted` event, `MediaKeys`, or a protection payload
  in the first segment) is stopped immediately and nothing is kept
- the locked folder is a *lock*, not encryption: files sit in the app's private storage where other
  apps cannot read them, and the password decides who sees them inside the app. A copy the user
  themselves asked for in a folder on the card is outside the app's reach and is not withdrawn by
  locking the file — the moment a file is locked, the app says so
- no analytics SDK, no ad SDK, no account system, and no network call other than a page load, a
  media probe, or a download the user started. Detection adds none: it reads response headers the
  page already received rather than probing URLs itself

## Verification

Claimed behaviour is covered by the cheapest layer that can actually execute it:

| Layer | What it proves | Where |
| --- | --- | --- |
| JVM unit tests (plus a Node harness for the injected page script, `scripts/test-media-sniffer.cjs`) | the recorder's segment writer, the recording port's message decoder, the origin rules the bridges are gated on, DASH manifest parsing and refusal rules, HLS/DASH segment reassembly against a real HTTP server (a `MockWebServer`) — including AES-128 decryption, `EXT-X-BYTERANGE`, variant selection and resumable staged parts — resolver HLS-variant expansion against a real server, rendition-id encoding, the download queue's rate/ETA, the export permission flow | `:core:model`, `:media:resolver`, `:media:downloader`, `:app` |
| Robolectric | The media-store export executed against the real framework at **API 24** (public-folder copy, media scanner, no-overwrite) and at **API 33** (`MediaStore` pending-flag protocol, byte fidelity) | `:app` `MediaStoreExporterTest` |
| Instrumented | The real store publish on a device (bytes read back through `ContentResolver`, the row queryable), a real **AES-128 HLS** download decrypted on-device with Android’s own JCE against a local server, and the launch smoke check | `.github/workflows/emulator_verification.yml` |

```bash
./gradlew :app:testDebugUnitTest :core:model:testDebugUnitTest \
          :media:resolver:testDebugUnitTest :media:detector:testDebugUnitTest \
          :media:downloader:testDebugUnitTest
# Needs a device or an emulator (KVM); the instrumented suite is what runs here:
./gradlew :app:connectedDebugAndroidTest
```

`MediaRemuxer` and `TrackMerger` use the platform codecs and `MediaMuxer`, so they can only be
verified on a device — that is the reason the emulator workflow exists.

## Modules

| Module | Contains |
| --- | --- |
| `:app` | screens, view models, services, DI, theme, resources |
| `:core:model` | `MediaCandidate`, `MediaInfo`, `MediaFormat`, `DownloadState`, `Tab`, `DashManifest`/`DashRenditionId`, and the pure detection rules (`MediaUrlRules`, `MediaSniffAdmission`) |
| `:core:database` | Room database, DAOs, entities, v1→v4 migrations |
| `:media:detector` | `DomMediaDetector` behind the `MediaDetector` interface |
| `:media:resolver` | `DirectMediaResolver` (HEAD, ranged GET, HLS variant parse) and `DashManifestParser` (pure, JVM-tested) |
| `:media:downloader` | `DownloadManager` (Range resume), `HlsDownloader` (VOD, AES-128, byte ranges) with the shared `SegmentedFetcher` (bounded-parallel, resumable), `DashDownloader`, `MediaRemuxer`, `TrackMerger`, `MediaMetadataReader` |

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

- `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` — **only** for the explicit “Save to
gallery” action on Android 9 and below, which publishes into the public `Movies`/`Music` folders.
It is requested in context, never at launch; declining it leaves the download, its share sheet and
the in-app player fully usable.

Downloads themselves need no storage permission: files are written to the app-scoped external
downloads directory (`Android/data/com.salvia.salviabrowxer/files/Downloads`), which the Settings
screen states verbatim, and each finished file is handed to `MediaScannerConnection` so it also
appears in the system downloads UI. Publishing a finished file to the gallery (`MediaStore`, on by
default) is permission-free on Android 10+, where the automatic step does it; on Android 9 and
below the automatic step skips silently and the “Save to gallery” action is the way to publish.

## Privacy

Pages are loaded by the sites you visit, downloads stay on the device, and the app has no account
and no analytics. The optional ad blocker decides locally, against a list that ships inside the app:
it fetches no list, reports nothing, and its counters stay in your settings. Backups and device transfer exclude the database, preferences, WebView cookies
and cache. **Private tabs** keep no history and are not restored, but they are not a separate
identity: cookies and site data are shared with normal tabs, the app says so in the tab switcher,
and no privacy claim here goes further than that. A short privacy policy lives in [`docs/privacy-policy.md`](docs/privacy-policy.md).

## License

GPL-3.0 — see [`LICENSE`](LICENSE). Selling the app is compatible with the GPL as long as
corresponding source is offered to recipients.
