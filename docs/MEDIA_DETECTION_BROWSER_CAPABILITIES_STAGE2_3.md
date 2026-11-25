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
- Persisted arbitrary request-header replay is not included. Existing same-profile cookie handling, User-Agent and Referer behavior remains in place. Do not replay Authorization, Proxy-Authorization, Cookie, or other credential-bearing headers from arbitrary page requests.

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
- Implement and test a durable, origin-bound safe request-header context before claiming full request-header replay. It must remain isolated from incognito and must never persist or replay credentials by default.