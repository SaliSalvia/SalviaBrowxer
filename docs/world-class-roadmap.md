# SalviaBrowxer — world-class release checklist

Status: roadmap, **not a feature list or a claim of InShot parity**. Product requirement update:
**no Save all**. The default download target should be the media associated with the user's
current page/post, not every file found in a feed. Keep GPL-3.0, honest refusals and the network
restrictions described in README. Do not add social-network extractors, token harvesting or DRM
bypass to make comparison numbers look good.

## P0 — verify the actual download loop before polishing

- **Feed/post attribution.** On-device matrix: X feed scrolled to a video, X post open, feed ad,
  pre-roll -> skip -> main video, a generic SPA, an ordinary MP4 page, a clear VOD HLS page.
  Log *locally and transiently* (never analytics) which candidate came from DOM metadata,
  element playback or response headers. Check that a visible-player hint corresponds to the
  correct post after scroll and navigation. Do not label a pre-roll as main content just because
  it fills the same player. If attribution is ambiguous, offer manual choice with clear labels.
  Test with both document-start injection and fallback injection. Cover iframe limitations; do not
  let unrelated tabs or stale pages leak candidates. Maintain admission checks for fragments,
  manifests, range reads, live and encrypted streams.
- **One-tap save when unambiguous.** When exactly one offerable rendition exists for the selected
  item, a deliberate download action can enqueue it without a redundant confirmation sheet.
  Never start a network download from visibility detection alone. Keep the quality picker when
  variants exist. Confirm requests do not falsely promise access to cookies/authenticated pages.
- **Gallery and Files.** Keep the Room queue and app copy as source of truth; ~~publish finished
  media using MediaStore pending rows on API 29+, configurable default-on export~~ **Shipped**
  (`MediaStoreExporter`, Settings → “Show downloads in the gallery”), and ~~an explicit per-item
  export action~~ **Shipped**: a completed row offers “Save to gallery” while the file is not in the
  store, and on API 24–28 that action requests `WRITE_EXTERNAL_STORAGE`
  (`maxSdkVersion="28"`) and publishes into the public `Movies`/`Music` folder instead. Both paths
  are executed for real by `MediaStoreExporterTest` under Robolectric at SDK 24 and SDK 33. Still
  open: ownership tracking for delete (never delete other apps' media), and a repeated on-device
  pass of the instrumented suite.
- **Playable output.** ~~Real local thumbnails~~ **Shipped** (`MediaMetadataReader` reads duration
  and a frame from the finished file; unavailable stays unavailable). ~~Remux clear VOD without
  re-encoding~~ **Shipped** (`MediaRemuxer` copies tracks into MP4; on failure the original stream is
  kept). Still open: confirming HLS/DASH output on a real device with the Media3 player, and not
  mislabelling TS as MP4 when a remux fails.
- **Privacy and workers (Phase 1 gates).** Test actual private cookies as well as history;
  isolate profiles or implement/disclose last-private-tab cleanup without session persistence.
  Assess page-owned worker response observation under the same admission rules, with no added
  requests. If safe coverage is unavailable, document the API/WebView gap. Neither is optional
  merely because UI polish is deferred.
- **Reliability.** Pause/resume across process death, missing/corrupt file recovery, disk full,
  cancellations, stalled server, revoked access, network changes, throttled progress persistence,
  retry without duplicates; explicit errors for unsupported formats. Cancellation and HTTP-failure
  mid-transfer are covered for the DASH and HLS paths by `DashDownloaderTest`/`HlsDownloaderTest`
  against a local server. Release gate: the required clean unit tests, assembleDebug, release lint
  and real-device matrix all pass — the last one needs a KVM-capable machine, which is what
  `.github/workflows/emulator_verification.yml` is for.

  The HLS path was strengthened to match the alternatives: an **AES-128** (non-DRM) playlist is now
  **decrypted** with the key the manifest names — key rotation and explicit/default IVs included —
  instead of refused, `EXT-X-BYTERANGE` segments are honoured, segments are fetched with bounded
  parallelism, and a paused or process-killed segmented transfer **resumes** from the parts it
  already staged (`SegmentedFetcher`). `SAMPLE-AES` (DRM-adjacent) and live playlists are still
  refused with honest errors. DASH segment transfers share the same parallel, resumable fetcher.
  The unit tests cover decrypt/rotate/range/resume against a real HTTP server; a real encrypted
  stream still needs the on-device pass.

## P1 — quality users notice every day

- Simplify home to paste field, one contextual action, live transfers and Library; accessibility
  at 360 dp / 1.3 font scale; Persian RTL layout and strings in the same patch.
- Consistent candidate tray with one prominent on-screen item **only when evidenced**, honest
  unknown/unsupported states, selected quality, title/poster from page only if attributable.
- Library: accurate title/thumbnail/duration/size, play/share/export/delete; download
  status, rate and ETA from service data; no dead settings rows.
- Player: preserve controls and accessible targets; PiP with lifecycle-safe pause/failure path
  on API 26+, back returns to Library. Check varying orientation and background playback policy.
- Theme discipline, typography, contrast and restrained motion per session prompt *after P0*.
  Validate with screenshots, TalkBack, zero-animation setting and font scaling.
- Private mode: don't advertise cookie isolation until a safe tested strategy is implemented.
  Shared WebView cookie jar on older APIs must be disclosed, not quietly called private.
- Privacy/security: no analytics or unexpected probes; scope FileProvider; verify backup excludes
  browsing and queue data; test redirects, untrusted filename/content type and file deletion.

## P2 — optional only if proven end-to-end

- ~~Clear finite MPEG-DASH VOD only~~ **Shipped**: `DashManifestParser` accepts a static,
  single-period, unprotected manifest with `SegmentTemplate`/`SegmentList`/single-file `SegmentBase`,
  `DashDownloader` segments it, and separated video/audio is muxed with `TrackMerger`. Dynamic,
  multi-period and protected manifests still get the refusal. Not yet covered: a repeatable on-device
  fixture comparison, and `SegmentBase` with `indexRange` byte ranges.
- Worker visibility only when a page-owned worker can be safely observed without extra requests
  or cross-app hooks. Document platform-specific gaps rather than claiming coverage.
- Release assets, adaptive icon, GPL source offer, privacy policy, accurate bilingual store copy,
  signed AAB from owner-controlled keystore, upgrade migration and a reproducible test report.

## Acceptance / claim policy

No claim of “detects every X video” or “better than InShot” without repeatable comparison on the
same URLs, Android/WebView versions and network. Measure: feed-to-candidate latency, correct
post attribution, false ad selection rate, download completion, gallery visibility, playability,
and failure explanations. Record negative results. Do not bump the version or advertise a capability
until it has shipped and passed the matrix.
