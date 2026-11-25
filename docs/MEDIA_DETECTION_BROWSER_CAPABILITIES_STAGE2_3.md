# Media Detection and Browser Capabilities — Stage 2/3

This branch is additive on top of the merged manifest download engine. It preserves the existing WebView, Room download queue, HLS/DASH planner, confidence scoring, candidate ranking, and incognito profile boundaries.

## Stage 2 — Media detection

Implemented:
- MIME-verified discovery for extensionless HTTP(S) audio/video resources observed through page fetch() and XMLHttpRequest.
- MIME hints flow into the existing probe path; URLs must still be verified HTTP(S) before becoming downloadable.
- MSE MediaSource.addSourceBuffer() and SourceBuffer.appendBuffer() metadata observation (MIME type and byte count only).
- MSE payload bytes are never copied into JavaScript, exposed through the bridge, or reconstructed. The bridge records metadata in DevTools only.
- Existing Confidence and MediaCandidateRanker logic is not replaced.

Boundaries:
- MSE SourceBuffer metadata does not make a blob: URL downloadable. A real network URL must independently be observed and pass the normal probe/URL policy.
- This branch does not bypass DRM, signed URL expiry, origin restrictions, or authentication challenges.
- Request-context assistance is additive. A bounded per-tab observer records only allow-listed, non-credential metadata from real GET/HEAD subresource requests. A request is promoted into the media context only after independent URL or MIME evidence identifies it as media.
- Only Accept, Accept-Language, and a validated Origin value can be captured. User-Agent and Referer continue to use the browser's existing download path; Cookie remains dynamically resolved from the selected WebView profile. Authorization, Proxy-Authorization, Cookie, Sec-* and arbitrary custom headers are never copied into this context.
- Context snapshots are limited to canonical HTTP(S) origins with media evidence. They are reused by the existing media probe, HLS/DASH manifest fetches, direct downloads, and manifest segment requests. Every redirect hop is checked again; metadata from one origin is removed before a cross-origin hop and is re-applied only when that destination has its own observed context.
- Normal downloads store the bounded allow-listed context in a dedicated Room table for up to 30 minutes, only after the user starts a download. It is removed on completion/cancellation or by expiry; download-row deletion cascades to its context row. Incognito context stays in memory only, scoped to the private tab/download, and is cleared with the tab.
- The context layer is optional: an empty, expired, or mismatched context leaves the established User-Agent, Referer, profile-cookie, direct-download, HLS/DASH, and fallback paths unchanged. It does not replace confidence scoring, ranking, probing, segment planning, or existing download execution.

## Stage 3 — Browser capabilities

Implemented:
- Persistent origin/resource-scoped allow grants for camera, microphone, and geolocation.
- Runtime Android permission checks are still required before a remembered web permission can be granted.
- Incognito tabs do not read or write the persistent site-permission store.
- HTTP Authentication requests receive a browser-owned username/password prompt. Credentials are not persisted.
- JavaScript alert, confirm, and prompt dialogs are handled by browser-owned dialogs.
- Popup creation requires WebView's isUserGesture signal; non-gesture requests are blocked.
- Native Android Print Framework action is available in the WebView surface; the system print UI supports printing and Save as PDF.

## Validation still required

- Run the full unit/Robolectric test suite and debug/release APK builds.
- Verify HTTP auth, JavaScript dialogs, geolocation/camera/microphone persistence, popup behavior, and Print-to-PDF on physical Android devices.
- Verify extensionless media detection against controlled test servers for video/*, audio/*, HLS, and DASH MIME types.
- Run the full unit/Robolectric test suite and debug/release APK builds on the latest head. Confirm Room 5→6 migration using an existing database.
- Verify safe request-context behavior against controlled same-origin and cross-origin test servers, including redirects, extensionless media, HLS variants/segments, DASH representations, expiry, retry, and Incognito cleanup.
- Physical-device verification remains required for browser callbacks, private-profile behavior, and Print-to-PDF.
- This is intentionally a constrained safe-header context, not arbitrary request replay. It does not bypass DRM, signed URL expiry, origin restrictions, or authentication challenges.