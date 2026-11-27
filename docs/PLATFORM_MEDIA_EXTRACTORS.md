# Platform Media Extractors (Instagram / TikTok / X)

This document describes the platform-page extractor layer added on top of the
existing Media Sniffer pipeline.

## Overview

The Media Sniffer historically detected media in two ways:

1. **Network interception** (hooked XHR/fetch + `shouldInterceptRequest`) which
   catches media URLs as the page loads them.
2. **DOM sniffing** (`<video>`, `<source>`, `<audio>`, inline blob resolution).

Some platforms render their media through obfuscated hydration payloads,
short-lived CDN URLs, or player components that never expose a plain
`<video src>` element. The **Platform Extractor** layer adds a third strategy:
when the active page is a supported platform *post page*, the engine fetches
the page HTML (with the tab's user agent and cookies) and parses the
platform-specific structures to produce high-confidence, directly downloadable
media URLs.

Supported platforms:

| Platform   | Matched hosts                                                        | Primary strategy |
|------------|----------------------------------------------------------------------|------------------|
| Instagram  | `instagram.com`, `instagr.am`, `cdninstagram.com`                    | `og:video` meta tags + embedded `video_versions` JSON |
| TikTok     | `tiktok.com`, `tiktokcdn.com`, `tiktokv.com`                         | `__UNIVERSAL_DATA_FOR_REHYDRATION__` hydration JSON (`webapp.video-detail`), `og:video` fallback |
| X (Twitter)| `twitter.com`, `x.com`, `twimg.com`                                  | Syndication endpoint (`cdn.syndication.twimg.com/tweet-result`) with `og:video` / `twitter:player:stream` fallback |

## Architecture

```
BrowserViewModel (onPageFinished / rescanActiveTabMedia)
        │  only when PlatformMediaExtractors.isSupported(url)
        ▼
MediaSnifferEngine.probePlatformPage()      (network, policies, merge)
        │  executeProbeRequest() with existing redirect/origin/cookie policy
        ▼
PlatformMediaExtractors.extract()           (pure parsing, no network)
        │  per-platform extractors
        ▼
DetectedMedia (source = PLATFORM_EXTRACTOR)
```

- `PlatformMediaExtractors` is a **pure parser**: it receives HTML text via a
  `fetchText` lambda and never performs network access itself. This keeps the
  class fully unit-testable and reuses the engine's existing request policy
  (timeouts, size limits, redirect handling, cookie scoping).
- Results are merged into the tab's existing `detectedMedia` list through the
  standard `mergeAndPrioritize` path, so ranking, quality grouping, and
  download plumbing are unchanged.
- A per-tab/per-URL cache key (`tabId|platform|url`) prevents duplicate probes;
  it is cleared on every navigation start and reload, mirroring the existing
  network-probe cache.

## Design constraints

- **Public content only.** The extractors parse what an anonymous visitor
  already receives. No login bypass, no credential handling, no DRM or
  encrypted-stream descrambling is attempted.
- **Fail closed.** Any parse failure returns `null`; no partial or guessed
  media is promoted. Media URLs are validated through the same sanitizer as
  the rest of the pipeline (`http/https`, no credentials, no `blob:`/`data:`,
  bounded length).
- **No page-content scraping into logs.** Fetched HTML is parsed in memory and
  discarded; nothing is written to DevTools buffers or history.
- **Least change.** No existing detection path was modified; the extractor
  layer only adds candidates. DOM/network detection continues to work on the
  same pages.

## Known limitations

- **Login-walled content** (e.g. private Instagram accounts, age-gated posts)
  is not supported, by design.
- **Short links** (`vm.tiktok.com`, `t.co`) are not resolved here; the probe
  follows the page's own redirect chain through the existing request policy
  when the page itself redirects.
- **X syndication endpoint** may return errors for some tweets (deleted,
  sensitive-content flagged, or rate limited); the `og:video` fallback then
  applies, and if that also fails the page simply yields no platform result
  while normal sniffing continues.
- **Adaptive (DASH/HLS) renditions** on these platforms are reported when the
  platform exposes plain `mp4` variants; the extractors do not assemble
  separate video/audio streams.

## Tests

`PlatformMediaExtractorsTest` covers URL matching, sanitizer rejection, and
each extractor's hydration/meta parsing paths with fake `fetchText` fixtures,
including fallback behavior when the primary strategy is unavailable.
