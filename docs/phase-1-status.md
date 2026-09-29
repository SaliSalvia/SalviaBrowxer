# Phase 1 implementation status

This is a partial implementation, not a release acceptance report. Version remains 1.0.0.
No Phase 2 or Phase 3 changes have been made.

## Implemented, pending Android build validation

- All five library modules now use compileSdk 36, matching the app; minSdk and JDK targets are unchanged.
- Paste routing validates HTTP(S) authority and routes the six specified social hosts and their subdomains to pages even if the path looks like media. Social paste routing does not authorize site-specific extraction.
- Added regression tests for social-host boundaries, malformed URLs and credential-bearing URLs.
- Android CI now runs the four requested unit-test tasks from clean outputs before assembling, and uploads test reports.
- Dynamic media-element source/visibility tracking, SPA page identity reset, tab-scoped admission,
  stronger-sighting dedupe upgrades and a visible-item hint in the bilingual tray. Unit tests
  cover foreground/background tabs, feed selection and SPA resets. No X compatibility claim
  without a real-device walkthrough.

## Validation in this sandbox

Both requested Gradle commands were attempted and failed before Gradle started: JAVA_HOME is not set and java is absent. Android SDK is also absent. Direct HTTPS probes to Google Android repositories and Maven failed during TLS connection. Package installation could not locate openjdk-17-jdk-headless. No compilation, Kotlin unit-test pass, device test or CI pass is claimed.
`node --test scripts/test-media-sniffer.cjs` passes all 14 synthetic tests against the embedded
script (inline discovery, dynamic insertion, scrolling, source changes, SPA reset, bounded feed
history, response headers/ranges, late responses, Blob/MSE provenance and revocation). These
are simulated browser APIs, not Android WebView or X integration tests. Resource XML parsing
and `git diff --check` pass. Kotlin regression tests were added but remain unrun.
Remote main was checked with `git ls-remote`: it still points to baseline 042e908.

## Additional detection safeguards

- Bounded URL-keyed candidate index retains new arrivals and visible evidence instead of keeping
  only old high-confidence feed entries. It does not establish post identity or classify ads.
- Request admission ignores outgoing MIME guesses, blobs and partial Range reads; fetch/XHR
  reject HTTP 206. DOM rejects segment URLs/MIME and web manifests.
- Blob evidence is per object URL. MSE/unproven blobs are refused; revocation clears candidates
  and matching selections. No MSE recording or hidden manifest lookup was added.
- Navigation/tab changes clear stale selections, originating-tab HTML results are checked, and
  visible players reannounce their evidence. Workers and same-URL navigation races remain
  integration-test gaps; URL/tab matching is not a document-generation token.


## Remaining release blockers

- Gallery export, ownership tracking, safe deletion and bilingual settings/actions.
- Single-item save path and clear choice when a pre-roll and main video are both present.
  Save-all is explicitly out of scope per the user's updated request.
- Real local thumbnails and library/home integration.
- HLS remux with original-file fallback and tests.
- Worker visibility assessment and documented API limitations.
- Private-session cookie isolation, accurate subtitle and runnable tests.
- Remaining required tests, clean Android build and device walkthrough.

Do not bump to 1.1.0 / versionCode 3 or begin visual redesign until Phase 1 passes its acceptance checks. DASH remains recognized and refused.
