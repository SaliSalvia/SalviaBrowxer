# Contributing to SalviaBrowxer

Read `README.md` first — not the whole thing, but the sections describing what the app refuses to
do. Contributions are judged against those refusals as much as against the code.

## The three rules that are not negotiable

1. **No site-specific extractors, token harvesting or DRM bypass.** No yt-dlp wiring, no
   per-site URL mangling, no "just this one site" special cases. The app saves what a page itself
   serves to the browser, and that is the product.
2. **Evidence over enthusiasm.** A capability lands with the test that proves it, or it does not
   land. A claim in the README ("Shipped") without a test or a device run behind it is a bug in
   the documentation.
3. **Honest refusals.** Live streams, protected manifests and unsupported containers are refused
   with an explanation, not converted into files that will not open. If your change removes a
   refusal, explain why the refusal was wrong.

## Before you push

- `./gradlew :app:testDebugUnitTest` (or the CI run) must be green. `--continue` keeps the whole
  report even when a module fails — read it as evidence, not noise.
- `node --test scripts/test-media-sniffer.cjs` must stay green. It is the only test that runs
  without a JDK, and it guards the injected sniffer script.
- `node --test` also parses the media rules out of `MediaUrlRules.kt`; if you change rule sets,
  both languages of the truth (Kotlin and the injected JavaScript) must change together.
- New user-visible strings go into **both** `values/strings.xml` and `values-fa/strings.xml`.
  The key sets are kept 100% identical on purpose.
- Kotlin style: match the file you are editing. Comments explain *why*, and the repository's
  history of fixed bugs lives in those comments — do not delete them when refactoring.

## What is especially welcome

- Tests for anything in `docs/world-class-roadmap.md` that is marked "still open".
- Device-matrix results (10 sites × 3 runs) recorded as issues, including negative results.
- Translations for new locales (a full `values-<locale>/strings.xml` mirroring every key).
- Fixes for the findings in `docs/analysis-v2-fa.md` that are still marked open.

## Pull requests

- One logical change per PR. The commit history of this repository is deliberately readable.
- The title says what the user gets ("downloads: resume after the Android 15 data-sync cap"),
  not what the diff does.
- If CI is red for a reason that is not yours, say so in the PR; do not rebase it away.
