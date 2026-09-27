# Play Store assets and listing copy

Everything here is instructions, not generated art. Do not generate trademark-infringing
screenshots (no YouTube, Instagram, TikTok or Netflix UI in any image).

## Listing copy rules

Allowed framing:

> SalviaBrowxer is a fast, private Android browser that saves videos and audio a page already
> offers you.

Forbidden framing — never ship any of these:

- "Download YouTube / Instagram / TikTok videos"
- "Download any video" or "works on every site"
- "Unlimited downloads", "bypass", "DRM bypass", "premium unlocked"
- claims about WorkManager, FFmpeg, yt-dlp, MPEG-DASH, AES-128 or live stream support

The app has no analytics, no ads and no account. Say that; it is true and it is the differentiator.

## Screenshot set (phone, 1080 × 1920 or larger)

1. Browser with the address bar focused and the media pill visible.
2. Media tray open on a real HLS master playlist, showing resolution rows and an honest
   "Size unknown" row.
3. Download queue: one active transfer with a percentage, one completed item.
4. In-app player on a finished download.
5. Settings: Wi-Fi only, allow insecure sites, and the real download directory path.
6. Persian UI screenshot (RTL) once `values-fa` ships.

Capture on a real device with a real page. No mockups, no invented thumbnails, no fake percentages.

## Adaptive icon

- 108 dp adaptive icon, WebP, all required densities (`mdpi`–`xxxhdpi`).
- Background: flat matte charcoal `#0A0A0C`.
- Foreground: the orbital ring mark, inside the 66 dp safe zone so no launcher mask clips it.
- Keep each layer under ~100 KB; the old build shipped one ~750 KB PNG copied three times with only
  `mipmap-hdpi` present. Replace, do not append.

## Feature graphic

- 1024 × 500 PNG or JPEG, no transparency.
- Charcoal background, the wordmark, one line of copy from the allowed framing above.
- No device frames containing third-party app UIs, no stock-photo people.

## Release checklist

1. `versionCode` incremented; `versionName` bumped from `0.9.0` only when the definition of done in
   `docs/prompts/01-world-class-v1-execution-prompt.md` is met.
2. `./gradlew :app:bundleRelease` produces a signed `app-release.aab` (or upload the unsigned AAB and
   let Play signing apply).
3. `docs/privacy-policy.md` placeholders replaced and hosted at a public URL.
4. Data safety form: no data collected, no data shared; browsing and downloads stay on device.
5. Store listing shows the privacy policy URL.
