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
- **One-tap save when unambiguous.** **Shipped**: a single offerable rendition enqueues the
  download from the action itself, and the quality sheet appears only when there is a choice to
  make. Never start a network download from visibility detection alone. Confirm requests do not
  falsely promise access to cookies/authenticated pages.
- **Gallery and Files.** Keep the Room queue and app copy as source of truth; ~~publish finished
  media using MediaStore pending rows on API 29+, configurable default-on export~~ **Shipped**
  (`MediaStoreExporter`, Settings → “Show downloads in the gallery”), and ~~an explicit per-item
  export action~~ **Shipped**: a completed row offers “Save to gallery” while the file is not in the
  store, and on API 24–28 that action requests `WRITE_EXTERNAL_STORAGE`
  (`maxSdkVersion="28"`) and publishes into the public `Movies`/`Music` folder instead. Both paths
  are executed for real by `MediaStoreExporterTest` under Robolectric at SDK 24 and SDK 33.
  **Ownership for delete is shipped** (`DownloadArtifactCleaner`): a file is removed only when it is
  provably ours - under the app's own directory, a MediaStore row carrying our `OWNER_PACKAGE_NAME`,
  a document under a persisted grant, or a legacy copy in `Movies`/`Music/SalviaBrowxer` - and
  anything else is reported as skipped rather than deleted. Clear-completed/failed/all delete the
  files of exactly the rows they clear (`DownloadsViewModelTest`). Still open: a repeated on-device
  pass of the instrumented suite.
- **Playable output.** ~~Real local thumbnails~~ **Shipped** (`MediaMetadataReader` reads duration
  and a frame from the finished file; unavailable stays unavailable). ~~Remux clear VOD without
  re-encoding~~ **Shipped** (`MediaRemuxer` copies tracks into MP4; on failure the original stream is
  kept). Still open: confirming HLS/DASH output on a real device with the Media3 player, and not
  mislabelling TS as MP4 when a remux fails.
- **Formats, destination and conversion.** **Shipped**: every format the competitor publishes is
  classified by exactly one path - media extensions to the detection tray, `doc/xls/pdf/txt` (and the
  rest of the office set) to the WebView download listener - and `FormatListParityTest` proves the two
  lists are disjoint. The folder a user picks can now be the *destination* rather than a second copy:
  the transfer still runs against private storage, and the finished file is moved into the tree with
  a length-verified copy before the private file is deleted (`DestinationPolicy`,
  `SafDocumentExporter.moveInto`); an unreachable folder costs nothing but a message. Conversion is
  real encoding and always the user's decision: H.264/MP4 for video, AAC/M4A for audio, source
  resolution/fps preserved, output written next to the original under a new name
  (`TranscodePlan`, `VideoTranscoder`). AVI/WMV/FLV/VOB have no platform demuxer and refuse with a
  reason instead of pretending. Untested until the device matrix: that the encoders behave on real
  hardware across vendors. See `docs/inshot-parity-fa.md` for the feature-by-feature table and its
  public sources.
- **Privacy and workers (Phase 1 gates).** Test actual private cookies as well as history;
  isolate profiles or implement/disclose last-private-tab cleanup without session persistence.
  Assess page-owned worker response observation under the same admission rules, with no added
  requests. If safe coverage is unavailable, document the API/WebView gap. Neither is optional
  merely because UI polish is deferred.
- **Reliability.** Pause/resume across process death, missing/corrupt file recovery, disk full,
  cancellations, stalled server, revoked access, network changes, throttled progress persistence,
  retry without duplicates; explicit errors for unsupported formats. Cancellation and HTTP-failure
  mid-transfer are covered for the DASH and HLS paths by `DashDownloaderTest`/`HlsDownloaderTest`
  against a local server, and a failed segmented transfer is auto-retried without re-fetching its
  completed parts (`DownloadService.transferWithRetry`). Release gate: the required clean unit tests, assembleDebug, release lint
  and real-device matrix all pass — the last one needs a KVM-capable machine, which is what
  `.github/workflows/emulator_verification.yml` is for.

  The HLS path was strengthened to match the alternatives: an **AES-128** (non-DRM) playlist is now
  **decrypted** with the key the manifest names — key rotation and explicit/default IVs included —
  instead of refused, `EXT-X-BYTERANGE` segments are honoured, segments are fetched with bounded
  parallelism, and a paused or process-killed segmented transfer **resumes** from the parts it
  already staged (`SegmentedFetcher`). A segmented transfer that fails on a transient network error
  is now **auto-retried** a bounded number of times with backoff, resuming from those staged parts;
  a DASH rendition that already completed is not fetched again. `SAMPLE-AES` (DRM-adjacent) and live
  playlists are still refused with honest errors. DASH segment transfers share the same parallel,
  resumable fetcher. The unit tests cover decrypt/rotate/range/resume against a real HTTP server;
  an encrypted HLS stream is also downloaded and decrypted **on a device**
  (`EncryptedHlsInstrumentedTest`, run by the emulator workflow) so Android's own JCE performs the
  decryption for real.

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
- Private mode: **disclosed, not claimed**: the README, the privacy policy and the in-app text say
  what a private tab does *not* do (cookies are shared with the normal session; history and the tab
  itself are not persisted). Isolating the jar safely is still open, and until it lands the wording
  stays honest rather than aspirational.
- Privacy/security: no analytics or unexpected probes; scope FileProvider; verify backup excludes
  browsing and queue data; test redirects, untrusted filename/content type and file deletion.

## P2 — optional only if proven end-to-end

- ~~Clear finite MPEG-DASH VOD only~~ **Shipped**: `DashManifestParser` accepts a static,
  single-period, unprotected manifest with `SegmentTemplate`/`SegmentList`/single-file `SegmentBase`,
  `DashDownloader` segments it, and separated video/audio is muxed with `TrackMerger`. Dynamic,
  multi-period and protected manifests still get the refusal. `SegmentBase` **with `indexRange`** is
  now covered too: the `sidx` range is fetched and parsed for byte ranges, and both the index and the
  single media range are asserted against a local server (`DashDownloaderTest`). Not yet covered: a
  repeatable on-device fixture comparison.
- Worker visibility only when a page-owned worker can be safely observed without extra requests
  or cross-app hooks. Document platform-specific gaps rather than claiming coverage.
- Release assets, adaptive icon, GPL source offer, privacy policy, accurate bilingual store copy,
  signed AAB from owner-controlled keystore, upgrade migration and a reproducible test report.

## Acceptance / claim policy

No claim of “detects every X video” or “better than InShot” without repeatable comparison on the
same URLs, Android/WebView versions and network. Every statement about the competitor in this
repository must carry a public source (store listing or published review) next to it: the
competitor's binary is never inspected, and “the competitor does X” without a citation is not a
finding. `docs/inshot-parity-fa.md` is the register of those claims. Measure: feed-to-candidate latency, correct
post attribution, false ad selection rate, download completion, gallery visibility, playability,
and failure explanations. Record negative results. Do not bump the version or advertise a capability
until it has shipped and passed the matrix.
