# Agent handoff

Read `README.md`, `phase-1-status.md`, `world-class-roadmap.md` and `inshot-parity-fa.md` before
changing anything. This branch continues the existing Compose app; it does not replace it.

## First actions for the next agent

1. Run `gh pr checks <this PR>` and fix anything red **before** adding features. The Gradle unit
   test step keeps `--continue`; `:app:lintDebug` is a gate and deliberately does not.
2. `node --test scripts/test-media-sniffer.cjs` must stay green (30/30). It is the only test that
   runs without a JDK.
3. Read the failing test report as *evidence*, not as noise. Two commits on this branch exist only
   because a test that had never been executed was wrong: a shadowed counter field and a test URL
   that was a playlist on purpose. Compile the pure sources before pushing when you cannot build.
4. Treat the device matrix as the owner's job: 10 sites × 3 runs, same network and URLs as the
   competitor, results recorded even when negative. Nothing here claims parity before that.

## Validation available outside CI

- Kotlin sources can be parsed and the pure classes can be executed without Android: the Kotlin
  compiler frontend is enough to catch syntax errors, and the pure JVM classes
  (`MediaUrlRules`, `AdBlockRules`, `AdBlocker`, `TranscodePlan`, `DestinationPolicy`) plus their
  JUnit tests compile and run against a minimal `org.junit` shim. `app/`, `media/*` and Compose code
  still needs the real build.
- Android CI: unit tests (app + model + resolver + detector), `--continue`, reports uploaded.
- APK workflow: debug APK artifact plus a signed release APK when the signing secrets are present;
  the artifact name says which one it is.
- Emulator workflow: instrumented suite on a KVM runner - encrypted HLS, the MediaStore publish and
  its ownership rules, the conversion, the SAF refusals. A codec or a provider that fails leaves its
  reason in the device log, which the job reports with the JUnit failures.

## Open gates

- Real-device matrix and the download/play/gallery/delete loop; notification-denied path; Persian
  RTL at 360 dp and font scale 1.3.
- Encoder behaviour on real hardware. The conversion path is now exercised on an emulator
  (`VideoTranscoderInstrumentedTest`, four cases) - vendor hardware is still open.
- The system picker's own grant and a real card's provider: the writable half of the destination is
  covered against a documents provider that only debug builds contain
  (`SafMoveInstrumentedTest` / `DebugDocumentsProvider`), which emulates the behaviour of a folder,
  not the grant that makes one.
- A process killed between the copy and the delete: the design keeps both copies and leaves the
  private one whole, but a test needs a real kill in the middle of a copy.
- Private-mode cookie isolation: the wording is honest today; the isolation is not implemented.
- Worker visibility assessment: document the WebView gap rather than claiming coverage.
- Live-stream capture: out of scope until a public source shows the competitor doing it and the
  owner lifts the boundary. Today no such source exists (`inshot-parity-fa.md`, W1–W4).
- Store assets: the signed AAB from the owner's keystore and the console screenshots.

## Standing boundaries

No site-specific extraction, private APIs, signature/token harvesting, yt-dlp, DRM bypass,
encrypted-HLS key recovery, live downloads or detection probes. Only observe media actually
exposed/loaded in WebView. Keep GPL-3.0, `com.salvia.salviabrowxer`, minSdk 24, the Room queue and
foreground download service, resume, clipboard behaviour, eight live WebViews, the blob bridge, the
player and EN/FA. No destructive migrations, no new modules, no WorkManager replacement.

Every claim about a competitor needs a public source next to it: never inspect their binary, and
never write "the competitor does X" without a citation. `docs/inshot-parity-fa.md` is where those
claims live.

Version stays 1.0.0 / code 2 until the acceptance matrix passes.
