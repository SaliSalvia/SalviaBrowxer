# SalviaBrowxer — execution prompt for a sellable v1

Paste this entire document into a coding agent that has the repository `SaliSalvia/SalviaBrowxer` checked out. Do not paste it into Lovable, v0, or any web-app generator. Those tools cannot ship this product.

You are a staff Android engineer working in this repository. Your job is to turn the current codebase into a truthful, Play-submittable, premium Android browser. You are not writing a new app, and you are not allowed to leave the product claiming features it does not have.

Read the code before editing. The README overclaims. Trust the Kotlin, the manifest, and the navigation graph over the README.

## Product

SalviaBrowxer is a fast, calm, private Android browser. Its signature is a precise media tray: when a page already exposes a downloadable file, an open HLS playlist, or a blob the user can read, the browser says so quietly and saves it reliably.

It is not a YouTube, Instagram, TikTok, or DRM downloader. It does not compete by ripping protected streams. It competes by being honest, fast, and finished.

Package: `com.salvia.salviabrowxer`. Keep the application id. Keep `minSdk` 24 unless a measured platform bug forces a raise, and document that reason. Today is after 31 August 2026: a new Play submission or update must `targetSdk` / `compileSdk` 36 (Android 16). Do not suppress the unsupported-compileSdk warning. Upgrade Android Gradle Plugin, the Gradle wrapper, and Kotlin together to a combination that officially supports API 36, then make `:app:assembleDebug` and the existing unit tests pass before any feature work. Prefer the Compose compiler Gradle plugin and a current Compose BOM over the pinned Kotlin 1.9.22 / compiler extension 1.5.8 pair, but do that upgrade as its own commit-sized step with a green build.

## Non-negotiable constraints

- Do not implement site-specific extractors, signature decryption, token harvesting, Widevine/FairPlay/PlayReady bypass, AES-128 HLS key recovery, or a real yt-dlp integration. `YtDlpResolver` is a stub that returns the page URL as `video/mp4`. Quarantine or delete it. It must not be constructible from the UI, and it must not ship in release.
- If a stream is DRM-protected, AES-128 HLS, live HLS, or otherwise not a file the page already exposed, fail with the existing honest strings (`error_drm_protected`, `error_unsupported_media`). Never save undecryptable bytes.
- Do not add a native FFmpeg binary in v1. It pulls license, APK size, and 16 KB page-size problems. `MediaProcessorImpl` currently returns false. Either implement a narrow remux with Media3 Transformer for files the downloader already wrote, or remove the processor from the user-visible promise. Do not leave a class that pretends to merge audio and video.
- GPL-3.0 is claimed in the README and in `strings.xml`, but there is no `LICENSE` file. Do not relicense. Add the official GPL-3.0 license text, and make the About screen able to open it. Selling the app is compatible with GPL only if corresponding source is offered to recipients. Do not add proprietary closed modules on top of this tree. If the owner later wants a proprietary license, stop and ask; do not change the license yourself.
- No analytics SDK, no ad SDK, no account system, and no network call that is not a page load, a media probe, or a download the user started. Privacy is the product.
- No new user-facing feature without a reachable UI path and a failure state. Dead switches are release blockers.
- Do not do a vanity move of every file into the empty Gradle modules. Those modules (`feature/*`, `core/network`, `core/storage`, `core/testing`) contain no feature code. For v1, delete empty modules from `settings.gradle.kts` and the app dependencies, and keep code where it already runs. A module earns existence only when it has a real public API and a caller outside itself. `media:detector`, `media:resolver`, and `media:downloader` qualify if their code stays there. `media:extractor` and `media:processor` do not, until they do real work.
- Preserve behavior that already works: DOM detection, WebView intercept, blob chunk reassembly, direct-file download with Range resume, non-encrypted VOD HLS segment concatenation, foreground `dataSync` service, FileProvider sharing, Room queue. Add tests around those paths before refactoring them.
- UI language for the agent may be English. The product must ship English and Persian (`values-fa`), with full RTL. Do not hardcode English in Compose. The string `"Bookmark added"` in `BrowserViewModel` is a bug.

## What is actually true today

Use this as the baseline. Do not rediscover it by trusting names.

Shipped and wired:

- One WebView in `BrowserScreen`. Address bar, back, forward, reload/stop, homepage, search engines in `Constants.SEARCH_ENGINES`, JavaScript toggle, cookie toggle, desktop user-agent.
- Navigation routes are only `browser`, `downloads`, `settings`.
- Media detection via `DomMediaDetector` plus request intercept plus `BlobDownloadBridge`.
- Quality sheet, `DirectMediaResolver` (HEAD, then ranged GET, HLS master variant list).
- `DownloadService` foreground downloads, pause/cancel, HLS via `HlsDownloader` (rejects AES-128 and live playlists).
- Room for downloads, bookmarks, and history. History is written unless the tab is marked private. DataStore settings exist.

Present but not a product:

- Tab model in `BrowserViewModel` (`createNewTab`, `switchTab`, `closeTab`) has no UI. There is one WebView. Switching tabs, if called, reloads the URL and destroys session state.
- `addBookmark` has no UI caller. History has no screen. Private mode has no control and does not isolate cookies, cache, or WebStorage.
- `HomeScreen` and `MediaPlayerScreen` are not in the `NavHost`. Media3 is a dependency with no playback path from a finished download.
- `JsMediaDetector.detect()` returns empty. `ExtractorEngine` is an interface with no implementation. `WebViewMediaDetector.detect()` returns empty.
- DASH is labeled in the resolver and then not segmented. Advertising it is a defect.
- `settings_wifi_only` is stored and never read by `DownloadService`.
- The dark-theme switch does not change theme. `SalviaBrowxerTheme` always uses `DarkColorScheme`.
- The download-directory row has an empty click handler. Files always go to app-scoped external storage, which is correct; the row must not pretend otherwise.
- `POST_NOTIFICATIONS` is in the manifest and never requested. On API 33+ the foreground download notification can be silently blocked.
- `usesCleartextTraffic="false"` with no explanation when the user submits `http://`.
- `fallbackToDestructiveMigration()` can wipe the database. A migration to version 2 exists and is then undermined.
- `recoverQueue()` requeues `QUEUED`, `RETRYING`, and `PREPARING` only. A process death during `DOWNLOADING` or `PAUSED` does not resume.
- Replacing the `Semaphore` instance when the user changes concurrency does not change permits already held, and can strand the queue. Fix the limiter without swapping the semaphore out from under in-flight work.
- `allowBackup="true"`, but backup rules only exclude shared preferences. Room and DataStore can leave the device. Exclude the database, DataStore, and WebView cookie/cache dirs from cloud backup and device transfer.
- WorkManager is a dependency and a README claim. Downloads use the foreground service. Remove the unused dependency or stop claiming it. Do not migrate to WorkManager unless you can prove the foreground-service path cannot meet the reliability bar; a second queue is worse than a fixed service.
- Launcher art is the same ~750 KB PNG copied three times, and only `mipmap-hdpi` exists. The adaptive icon uses that PNG as a foreground. Replace it with a real adaptive icon: matte charcoal background `#0A0A0C`, a simple mark inside the 66 dp safe zone, WebP, all required densities. Do not ship a 2 MB icon.
- Strings are English only. Typography is `FontFamily.Default`. There is no TalkBack pass, no predictive back, and no edge-to-edge `WindowInsets` handling worthy of API 36.
- Version is `1.0.0` / versionCode 1. That version is a lie until the definition of done below is met. Until then, versionName is `0.9.0`.

## Definition of done

A build is sellable only when every item below is true on a release AAB, not a debug APK.

1. `compileSdk` and `targetSdk` are 36. `bundleRelease` is minify-and-shrink clean. R8 does not strip Room converters, Hilt, or the download state enum. Existing ProGuard keep rule for `DownloadState` stays.
2. Cold start lands in the browser, not a splash gimmick. A URL intent (`ACTION_VIEW` for `http`/`https`) opens in a new tab. Share-into-app opens the URL. The download notification opens the queue, which already has an extra; keep that.
3. Tabs are real: each live tab has its own WebView state (history, scroll, form state). Hibernated tabs may be destroyed after a documented limit, but restoring them reloads the last committed URL and title, not a blank page. Open, switch, and close are one thumb away. Private tabs use a separate `CookieManager` profile or an equivalent isolation that does not write history, autofill, or the normal cookie jar. Closing the last private tab clears that jar.
4. Bookmarks and history have screens, search, delete, and open. Private mode never writes history. Clear-history and clear-cookies do what they say, including WebView cache where the label claims it.
5. Every settings row does something or is gone. Wi-Fi only pauses and does not start cellular transfers. Notification permission is requested in context, the first time a download is enqueued, with a denial path that still downloads and explains the missing notification. Cleartext is either allowed by an explicit user setting defaulting to off, or the address bar explains why `http://` failed. No silent failure.
6. Media tray lists candidates with title, kind, extension, and confidence only as an internal sort key — never as a fake percentage shown to the user. Quality rows show resolution or bitrate, container, and size when known. Unknown size says so. DRM, live, and encrypted HLS show the honest error and do not enqueue.
7. Direct files resume after process death. HLS VOD non-encrypted still concatenates, and the result is playable in the in-app player. DASH is either downloaded as a non-DRM VOD rendition or not offered. Blob saves still work and appear in the same queue.
8. The player opens from a completed audio or video item. It supports play, pause, seek, and rotate without leaking the player. It does not claim to be an editor.
9. Persian and English, RTL and LTR, font scale 1.3, TalkBack on the browser chrome, 48 dp targets, contrast at least WCAG AA for text on charcoal. Predictive back works on sheets and secondary screens.
10. No empty Gradle module remains. No user-visible string promises yt-dlp, FFmpeg, or WorkManager. README matches the binary.
11. Unit tests cover DOM detection, resolver HEAD-failure fallback, HLS variant parse, download-state transitions, private-mode history suppression, and Wi-Fi-only gating. A release build is what CI uploads, in addition to debug.
12. Privacy: no third-party tracker. Backup excludes browsing data. A short privacy policy is linked from About and states that pages are loaded by the sites the user visits, downloads stay on device, and the app has no account and no analytics.
13. `LICENSE` is GPL-3.0. About shows version from `BuildConfig`, not a hardcoded string.

## Visual bar

Implement this in Jetpack Compose in this repository. A web mock is not a source of truth. If a Lovable or Figma prototype exists, use it only for hierarchy, spacing, and tone. Re-measure every screen against a real WebView, the IME, gesture navigation, and a 360 dp phone.

The page is the product. Chrome recedes.

- Palette already in `Color.kt` stays the brand. Matte charcoal `#0A0A0C` background, pearl `#F8F7F4` text, silver `#C0C5CE` secondary text, aurora teal `#00DAC6` only for live state: progress, media-ready, focus. Nebula violet `#8B5CF6` only for private mode. Do not paint ordinary controls teal. Do not introduce a new accent.
- No glassmorphism over web content. No gradient mesh backgrounds. No bounce. Motion is 180–240 ms, emphasized decelerate, and disabled when the user has turned off animator duration.
- Default chrome: a collapsing top address field and a bottom bar of back, forward, tabs, and menu. Home, downloads, and settings are not bottom-bar peers. Downloads are a badge on the menu or on the media tray, not a permanent destination competing with back.
- The draggable floating button is not the default. It covers the page and reads as an overlay ad. Default media affordance is a quiet pill in the top bar that appears only when candidates exist, showing the count. Tapping opens the tray. Keep drag-position as an advanced setting if you must, off by default.
- Quality choice is a modal sheet with a grabber, a title, a thumbnail if one was actually detected, and rows. Do not invent thumbnails.
- Tab switcher is a grid of captured WebView previews, with close and private sectioning. If preview capture is too expensive on minSdk 24, use title, host, and a monogram. Do not fake a screenshot.
- Empty states are one sentence and one action. Errors say what failed and what to do. No "Oops".
- Ship a light theme only if it is finished. Otherwise remove the dark-theme switch and keep the dark identity. A switch that does nothing is worse than a single theme.
- Type: a licensed OFL face, shipped or downloaded via Google Fonts, not `FontFamily.Default`. Use one sans for UI. For Persian, use Vazirmatn (OFL) and set it when the locale is `fa`. Do not use a Latin face for Persian text.
- Iconography is Material Symbols, one weight, 24 dp. Do not mix filled and rounded at random.

## Work order

Do not start at the UI. A beautiful shell on a lying settings screen is not sellable.

### Phase A — toolchain

Upgrade AGP, Gradle, Kotlin, and Compose until `compileSdk` 36 builds. Fix only the breakages that upgrade causes. Run unit tests. Stop if the upgrade cannot be made green; do not paper over it with `suppressUnsupportedCompileSdk`.

### Phase B — truthfulness

Delete or unwind empty modules. Remove `YtDlpResolver` from any injection path and from release source if it has no honest use. Stop offering DASH until it works. Remove the dead dark-theme switch or implement both themes. Make the download-directory row state the real path and offer "export to Downloads" via the system picker instead of pretending the app writes public storage. Align README, About, and strings with this. Add `LICENSE`. Set versionName `0.9.0`.

### Phase C — correctness that can lose user data

- Request `POST_NOTIFICATIONS` in context.
- Enforce Wi-Fi only with `ConnectivityManager`; do not start, and pause running work, when the setting is on and the network is not Wi-Fi. Do not use a deprecated API without a version guard.
- Persist enough state to resume `DOWNLOADING` after process death. Requeue `PAUSED` only when the user resumes.
- Replace the racy semaphore with a limiter that honors the setting for new work and does not drop in-flight permits.
- Remove `fallbackToDestructiveMigration` from release. Keep a real migration path. Add a test that version 1 opens on version 2 without data loss if you still claim to support that upgrade; otherwise ship a fresh schema version and document that pre-release installs are wiped once, loudly, on upgrade.
- Exclude database, DataStore, and WebView data from backup and device transfer.
- Cleartext: user setting default off, plus an inline explanation on failure.
- Fix the blob and file download paths only if tests show a regression. Do not rewrite `HlsDownloader` for style.

### Phase D — browser product surface

- Multi-WebView tabs with a cap (start at 8 live WebViews, hibernate the least recently used). Restore hibernated tabs by URL.
- Tab switcher UI, close, new tab, private tab.
- Bookmarks screen and add/remove from the menu. History screen. Both open in the current tab.
- Find in page. Share page. Copy link. Desktop toggle already exists; keep it and show its state.
- External `VIEW` / `SEND` intents.
- Pull-to-refresh is optional. Do not add it if it fights vertical pages.
- One WebView client path. Delete the unused duplicate in `WebViewUtils` if `BrowserScreen` does not call it, or make `BrowserScreen` call it. Two divergent WebView setups are a defect.

### Phase E — media and player

- Tray UI as specified above. Wire `openQualitySheetFor` to a list, not only the first candidate.
- Show resolver results without blocking the sheet: the sheet opens immediately, rows refine when the HEAD/HLS parse returns, which the ViewModel already tries to do. Keep that. Never freeze the page.
- In-app player for completed files using the Media3 dependency already on the classpath. Back returns to the queue. Missing file shows an error and offers delete.
- DASH: implement non-DRM VOD only if you can do it without a native binary and with tests, or remove the label. No middle ground.
- Do not expand detection to obfuscated player APIs. The supported set is visible media URLs, media elements, open playlists, and blobs the page can read.

### Phase F — interface finish, Persian, accessibility

Apply the visual bar across browser, tabs, tray, downloads, bookmarks, history, settings, player, and about. Add `values-fa` for every string. Verify RTL by switching the device language, not by guessing. Support font scale. Add content descriptions that say what the control does, not what the icon is named.

### Phase G — release

- CI builds `bundleRelease` unsigned when secrets are absent, and signed when the existing env vars are present. Keep the keystore out of git.
- Upload the AAB, not only the debug APK.
- Generate a privacy-policy draft as a checked-in `docs/privacy-policy.md` the owner can host. Do not invent a company address. Use placeholders the owner must replace: legal name, contact email, effective date.
- Store listing copy must not say "download YouTube" or "any video". It may say "save videos and audio a page already offers you".
- Adaptive icon and feature graphic notes live in `docs/store/`. Do not generate trademark-infringing screenshots.

## Acceptance checks the agent must run

Before calling the work done, run unit tests and a release compile. Then manually trace, in code review if a device is unavailable, and on a device if one is:

- Search "kotlin coroutines", open the result, go back, bookmark it, find it, delete it.
- Open three tabs, rotate, kill the process, restore.
- Open a private tab, visit a page, confirm history did not grow, close private tabs, confirm the private cookie jar was cleared.
- Detect a direct MP4, enqueue, deny notifications, confirm the file still completes, then allow notifications and confirm progress appears.
- Toggle Wi-Fi only and confirm a cellular-only device does not start the transfer.
- Feed an AES-128 playlist and confirm an honest error and no output file.
- Complete a download, play it, rotate, share it, delete it.
- Switch the device to Persian and confirm no clipped English, no LTR-only padding, and a Persian UI font.
- Confirm `http://example.com` either loads under the explicit setting or shows a specific message.

## Out of scope for v1

Sync, accounts, extensions, ad blocking, reader mode, password manager, desktop build, Wear OS, payment, subscriptions, crash-reporting SaaS, site-specific extractors, DRM, background play of arbitrary web pages, and a redesign that replaces charcoal and teal with a generic purple gradient.

If a task is not on the definition of done, do not build it.

## Way of working

- Small changes. Each phase leaves the app building.
- No drive-by reformatting of unrelated files.
- If a phase-A upgrade fails for a reason you cannot fix in this tree, stop and report the blocker. Do not continue to the UI.
- If you find a security issue in `JavascriptInterface` or `FileProvider` (the provider currently exposes broad `external-files-path`, `files-path`, and `cache-path` roots), narrow the paths to the download and blob directories as part of phase C. Do not widen them.
- Do not add a new architecture framework. Hilt, Room, DataStore, Coroutines, and Compose are the stack.
- When the README and the code disagree, fix both in the same change.
