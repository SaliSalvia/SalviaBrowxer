# Agent handoff — partial Phase 1

This checkpoint is not Phase 1 completion or a verified fix for X. Read README.md,
phase-1-status.md and world-class-roadmap.md before making changes. Continue from
latest main after this PR is merged, preserving the existing Compose app.

## First actions

1. Use JDK 17 and Android SDK 36. Run the required tests and debug build below;
   inspect this PR's CI results and fix compile/test failures before adding features.
2. Reproduce X inline-feed detection on an Android device/WebView. Record version,
   document-start support and observed candidate evidence without retaining cookies,
   tokens or private URLs in logs. No real X/device reproduction was available here.
3. Review tab restore/back-forward behavior, same-URL navigation races, fallback
   injection, iframe/worker limitations and late asynchronous quality resolution.
   Tab/page URL checks are not document-generation tokens.
4. Distinguish active-player relevance from actual post/main-content attribution.
   A pre-roll may occupy the visible player. Do not auto-select it as main content.

## Checkpoint validation

- `node --test scripts/test-media-sniffer.cjs`: 14 synthetic tests passed.
- Android resource XML parsing and `git diff --check`: passed.
- Kotlin regression tests added, **not executed**; Gradle could not start because
  Java was absent. Android SDK was absent too. No APK or Android/X test result.
- Version remains 1.0.0 / code 2; all libraries now target compileSdk 36.

Required commands (do not replace these with the Node harness):

```sh
./gradlew clean
./gradlew :app:testDebugUnitTest :core:model:testDebugUnitTest :media:resolver:testDebugUnitTest :media:detector:testDebugUnitTest
./gradlew :app:assembleDebug
node --test scripts/test-media-sniffer.cjs
```

## Remaining Phase 1 gates

- Main/post attribution and honest ambiguity handling; deliberate one-tap save for
  a single obvious offerable item. **Save-all is explicitly out of scope.**
- Gallery export: default-on setting for new installs plus explicit action;
  API29+ MediaStore pending/publish, app ownership tracking and safe deletion;
  older API document-picker fallback; keep app copy when export fails.
- Real poster/local-frame thumbnails; no invented artwork.
- Finished clear VOD HLS remux using Media3 on app-owned files, retaining original
  on failure. No native FFmpeg; audio-only only from a real supported source.
- Worker observation assessment under the same admission/no-extra-request rules;
  explicitly document unsupported platforms if safe hooking is unavailable.
- Private cookie isolation or disclosed/tested last-private-tab cleanup, no
  private-session persistence flush. History suppression alone is insufficient.
- Tests for gallery off, remux fallback, actual private cookies/history, refusal
  of unsupported/live/encrypted content; download/play/gallery/delete device loop,
  notification denial, Persian RTL at 360dp and font scale 1.3.

## Standing boundaries

No site-specific extraction, private APIs, signature/token harvesting, yt-dlp,
DRM bypass, encrypted HLS key recovery, live downloads or detection probes. Only
observe media actually exposed/loaded in WebView. Keep GPL-3.0, application ID,
minSdk24, data/migrations, existing foreground download service/Room queue, resume,
clipboard behavior, eight live WebViews, blob bridge, player speed/order and EN/FA.
No destructive migrations, new modules or WorkManager replacement.

Phase 2 clear finite DASH is optional only after Phase 1 passes; until tested with
playable output, keep DASH recognized/refused. No Phase 3 decoration before Phase 1
green. Bump to 1.1.0 / code 3 only when Phase 1 lands with truthful README updates.
The roadmap's P0/P1/P2 labels express priority, not authorization to skip these gates.
