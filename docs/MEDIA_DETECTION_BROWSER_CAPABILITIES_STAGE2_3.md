# Media Detection and Browser Capabilities — Stage 2/3

This branch is additive on top of the merged manifest download engine. It preserves the existing WebView architecture, Room download queue, HLS/DASH planner, direct/range downloader, confidence scoring, candidate ranking, and Incognito profile boundaries.

## Stage 2 — Media detection and download assistance

Implemented:
- MIME-verified discovery for extensionless HTTP(S) audio/video resources observed through page fetch() and XMLHttpRequest. MIME hints flow into the existing probe path, and the normal HTTP(S) source verification is still required.
- MSE MediaSource.addSourceBuffer() and SourceBuffer.appendBuffer() provenance observation. WeakMaps associate the exact ArrayBuffer returned by fetch/XHR (or an ArrayBuffer obtained from a response Blob) with that response's final HTTP(S) URL. The URL is reported only when that same backing buffer is passed to appendBuffer; byte data is never read, copied, exported, or reconstructed. XHR attribution requires the final `responseURL`; it never falls back to the pre-redirect request URL. New/transformed allocations without exact provenance remain unattributed rather than guessed. Only complete HTTP 200 responses are eligible; 206 and any `Content-Range` response are rejected, and common HLS/DASH segment suffixes are never promoted as standalone downloads.
- Existing Confidence and MediaCandidateRanker logic remains intact. The helper does not replace URL discovery, DOM scanning, probing, candidate ranking, manifest parsing, segment planning, or existing downloader paths.
- A bounded, short-lived, per-tab observer captures only allow-listed, non-credential metadata from real GET/HEAD subresource requests. A request is promoted only after independent URL or MIME evidence identifies it as media.
- Only Accept, Accept-Language, and a validated HTTP(S) Origin value are eligible for the helper. User-Agent and Referer retain their existing browser/downloader path; Cookie remains dynamically resolved from the selected WebView profile. Authorization, Proxy-Authorization, Cookie, Sec-* and arbitrary custom headers are never copied into this helper context.
- Context snapshots are restricted to canonical HTTP(S) origins and media-evidenced requests. The existing media probe, HLS/DASH manifest fetches, direct downloads and HLS/DASH segment requests can consume the optional context.
- Each redirect hop is origin-checked. Helper headers are removed on cross-origin redirects and are re-applied only when the redirect destination has its own captured media context.
- If an injected helper header is followed by a selected header-related failure (400, 403, 406, or 415), the probe or downloader retries that request once through the established path without helper headers. Normal successful requests are not retried.
- Normal downloads store the bounded allow-listed context in a dedicated Room table for at most 30 minutes after explicit user action. Context is removed on completion/cancellation or expiry; deleting the download cascades to its context row. Incognito context is memory-only, tab-scoped, and cleared with the tab.
- An empty, expired, or mismatched context leaves the established User-Agent, Referer, profile-cookie, direct-download, HLS/DASH, and fallback paths unchanged. Generic non-media downloads do not consume media header context.

Boundaries:
- MSE SourceBuffer metadata does not make a blob: URL downloadable. A real network URL must independently be observed and pass the normal probe/URL policy.
- This is constrained safe metadata assistance, not arbitrary request replay. It does not reconstruct MSE buffers, bypass DRM, evade signed URL expiry, bypass origin restrictions, or bypass authentication challenges.

## Stage 3 — Browser capabilities

Implemented:
- Persistent origin/resource-scoped allow grants for camera, microphone, and geolocation, with runtime Android permission rechecks.
- Incognito tabs do not read or write the persistent site-permission store.
- HTTP Authentication requests receive a browser-owned username/password prompt. Credentials are not persisted.
- JavaScript alert, confirm, and prompt dialogs are handled by browser-owned dialogs.
- Popup creation requires WebView's isUserGesture signal; non-gesture requests are blocked.
- Native Android Print Framework action is available in the WebView surface; the system print UI supports printing and Save as PDF.

## Validation

- The earlier head b559f4e passed the configured Unit Test, Debug APK, Release APK, artifact verification, and artifact upload workflow stages.
- The latest head must also pass the same workflow before this PR is considered CI-validated.
- Physical-device verification is still required for real-site media behavior, Room 5→6 migration using an existing database, Incognito isolation, HTTP auth, JavaScript dialogs, camera/microphone/geolocation permissions, popup behavior, and Print-to-PDF.
- JVM MockWebServer integration tests exercise same-origin and cross-origin redirects, exact destination-origin context reapplication, credential stripping, and preserved legacy cookie/Referer behavior. The shared request executor is tested against an actual 403→200 legacy retry, successful responses without extra retries, and non-retryable 500 responses. MSE policy tests cover extensionless URLs, MIME agreement, opaque URLs, partial/range responses, and segment rejection.
- Controlled Android/WebView tests are still required to verify real player behavior across fetch/XHR/Blob implementations, transformed buffers, HLS/DASH variants/segments, Room migration, Incognito lifecycle, browser callbacks, and Print-to-PDF.
