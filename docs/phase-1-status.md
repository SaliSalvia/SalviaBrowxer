# Phase 1 implementation status

This is an implementation status, not a release acceptance report. Version remains 1.0.0 / code 2
per the roadmap policy: **no bump until the real-device matrix passes.**

## What is implemented and covered by tests that run

- **Detection and admission.** Four layers (response sniffing in `shouldInterceptRequest`, MSE/blob
  observation behind a user action, DOM evidence, direct resolver) with admission rules that refuse
  fragments, manifests, range reads, live and encrypted-by-default streams. `node --test
  scripts/test-media-sniffer.cjs`: 27/27 against the embedded page script.
- **Download queue.** `DownloadWorkQueue` (parallel parts, `SlotLimiter`, retry ≤ 3 resuming from
  staged `.part<N>` files, idempotent start, queued items survive `stop`), Wi-Fi hold with
  `WIFI_HOLD_MARKER` released when Wi-Fi returns. `DownloadWorkQueueTest` and the extracted service
  collaborators are asserted behaviourally, with no network and no MockK in `:media:downloader`.
- **HLS and DASH.** AES-128 decrypt with key rotation and explicit/default IV, `EXT-X-BYTERANGE`,
  bounded parallel segments, resume from staged parts, bounded auto-retry; DASH static
  single-period unprotected manifests including single-file `SegmentBase` with `indexRange`
  (index parsed, ranges asserted) and separated video/audio muxing. Live, dynamic, multi-period
  and protected manifests are refused with explicit errors.
- **Library, gallery, deletion.** Room queue is the source of truth; MediaStore publish on API 29+
  and public-folder copy on 24–28 (Robolectric at both SDKs); delete goes through
  `DownloadArtifactCleaner`, which removes only provably app-owned files (app directory, MediaStore
  row with our `OWNER_PACKAGE_NAME`, document under a persisted grant, legacy
  `Movies`/`Music/SalviaBrowxer`) and reports anything else as skipped.
- **Formats.** Every published competitor format is owned by exactly one path: media to the tray,
  documents (`doc/xls/pdf/txt` and the office set) to the WebView download listener;
  `FormatListParityTest` asserts the split is complete and disjoint, and the mime map covers both.
- **Destination.** "Save into the chosen folder" moves the finished file into the user's SAF tree
  (`DocumentsContract` create → stream → verify reported length → delete the private file) and
  points the row at the document; an unreachable tree keeps the file private and says so
  (`DestinationPolicy`, `DestinationPolicyTest`). The refusal half runs on a device as well
  (`SafDestinationInstrumentedTest`: a provider that is not installed, a tree this app holds no
  grant for, an empty file, and the tree name the settings row shows). The writable half runs on a
  device too, against a documents provider that exists only in debug builds
  (`SafMoveInstrumentedTest` via `DebugDocumentsProvider`: a name the provider renames on collision
  and the row follows, a provider that keeps only part of what it accepted and the move is refused
  with nothing left behind, and a provider that refuses to create at all). What is still not covered
  is the system picker's own grant — that is the user's hand — and a real card's provider.
- **Conversion (real encoding, opt-in per file).** `TranscodePlan` decides, `VideoTranscoder`
  re-encodes to H.264/MP4 or AAC/M4A from any container the platform can demux; AVI/WMV/FLV/VOB and
  friends refuse with a reason because Android has no demuxer for them. The original is never
  overwritten and a failed run leaves nothing behind. On a device this is exercised end to end
  (`VideoTranscoderInstrumentedTest`: an H.264 file built by the device's own encoder converted and
  read back through `MediaExtractor`, a hand-written PCM WAV becoming one AAC track, a file no codec
  can open failing, and a cancelled run leaving no output).
- **Ad and tracker blocking (opt-in, default off).** The sequence lives in `RequestInterception`, so
  the blocker's veto is applied before the media observer and the order is asserted in
  `RequestInterceptionTest` rather than described in a comment: a blocked request is never observed,
  media from a blocked host still is, and nothing the tray would offer is ever blocked — an invariant
  that failed the first time it was written (the observer admits a URL named `manifest.<x>` as a
  playlist while the blocker only looked at extensions, so the blocker now asks the app's own playlist
  rule). Media/playlist/segment URLs are never blocked, loopback/private and the page's own host never
  are, host matching is label-by-label, per-site exemption and a blocked counter exist; 26 pure cases
  across `AdBlockerTest` and `RequestInterceptionTest`, plus settings-persistence tests.
- **Privacy wording.** Private tabs are described by what they do not do (shared cookie jar, no
  history or tab persistence) in the README, the privacy policy and the app itself.

## What CI proves, and what it does not

- Android CI runs `:app:testDebugUnitTest`, `:core:model:testDebugUnitTest`,
  `:media:resolver:testDebugUnitTest`, `:media:detector:testDebugUnitTest` with `--continue`, then
  `:app:lintDebug` as a gate that fails the job on real lint errors, and uploads the reports.
- Emulator verification runs the instrumented suite on a KVM runner: the encrypted-HLS download
  performed by Android's own JCE, the real MediaStore publish and its ownership rules
  (`AndroidPublishedCopyInstrumentedTest` — a row this package inserted is owned and deletable, while
  another provider's document, a row that is not there and a file in the public download folder are
  refused), the conversion above, the destination refusals and the writable half above, and the
  blocker's ordering inside a real WebView (`WebViewInterceptionInstrumentedTest`). Every run also
  names what it skipped, so a test that an `assume` bypassed on a device is not counted as evidence. A failure inside a codec or a
  provider writes its reason to the device log, which the job reports alongside the JUnit failures.
- **Not proven anywhere yet:** the 10-site × 3-run comparison against the competitor on the same
  network and URLs, the encoder's behaviour on real vendor hardware, the system picker's own grant
  (the writable half of the destination is exercised against a debug-build provider, not against a
  real card's), a process killed between the copy and the delete, and the download/play/gallery/delete
  loop as a whole on a phone. Until the owner runs that
  matrix, no parity claim is made in any document. `docs/inshot-parity-fa.md` states this at the top.

## Boundary removals and additions in this branch

- Removed: the unreferenced `service/MediaDetectionService.kt`; the release APK and the two `tmp/`
  reports that were tracked but should not be.
- Kept out: no new module, no destructive Room migration (additive only), no WorkManager
  replacement, no site-specific extractors, no token harvesting, no DRM bypass, no live-stream
  recording.
- Added: ad blocking (opt in), destination-folder mode, format classification split, per-file
  conversion. Each of them can be turned off, and none of them changes what an untouched install
  downloads or where it puts it.

Do not bump to 1.1.0 / versionCode 3 until the acceptance matrix passes.
