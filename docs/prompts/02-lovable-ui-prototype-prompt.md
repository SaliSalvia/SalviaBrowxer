# Lovable prompt — visual prototype only

Paste everything inside the fenced block into Lovable. Do not paste the execution prompt, and do not treat Lovable's React output as the Android app. SalviaBrowxer ships as Jetpack Compose around a real WebView. This prototype exists to lock hierarchy, spacing, tone, and states before that Compose work.

After Lovable finishes, use the handoff prompt at the bottom of this file in the Android repository. Rebuild the screens. Do not port the React components.

```
You are designing a clickable mobile prototype of SalviaBrowxer, a premium native Android browser. Build a phone-framed prototype and a written design spec. You are not building a browser, a downloader, or a marketing landing page.

The webpage is the product. Chrome recedes. This must not look like a SaaS dashboard, a crypto wallet, or an AI-generated purple gradient app. No glassmorphism over the page. No mesh gradients. No neon glow. No bounce easing. No Inter. No three-column hero. No fake "Download any video from YouTube" claim. That feature does not exist and must not be designed.

PRODUCT
SalviaBrowxer quietly saves media a page already exposes: a direct video or audio file, an open HLS playlist, or a blob the page can read. It refuses DRM, encrypted streams, and live streams with a plain sentence. It is a browser first. Downloads are a consequence, not the home screen.

DEVICE
Design at 393 × 852 pt, safe areas included, as if it were a Pixel phone in gesture navigation. Also show the tab switcher and the quality sheet at 360 × 800, where horizontal space is tight. Put the phone in a simple device frame on a #0A0A0C canvas. No desktop layout. A 840 pt tablet note is optional and must not change the phone hierarchy.

BRAND TOKENS — use these exact values, do not invent a new palette
- Background: #0A0A0C
- Surface: #141418
- Surface raised: #1C1C20
- Elevated: #26262C
- Border: #2E2E34, 1 px
- Text: #F8F7F4
- Text secondary: #C0C5CE
- Text tertiary: #9AA0AE
- Accent, live state only: #00DAC6. Use it for progress, the media-ready pill, focus rings, and the selected quality row. Do not use it on ordinary icons.
- Private mode only: #8B5CF6. The rest of the app never turns violet.
- Error: #FF5252 on #4A0D0D
- Radius: 16 for sheets and cards, 999 for pills, 12 for rows
- Spacing: 4 pt grid. Screen padding 16. Touch targets 48 minimum.
- Type: "Instrument Sans" for UI. Persian sample uses "Vazirmatn". Weights 400 and 560 only. Sizes: 13 label, 15 body, 17 field, 22 screen title. No display type inside the app.
- Icon set: one style, 24 pt, 1.75 stroke if stroked. Do not mix filled and outlined.
- Motion: 200 ms, ease-out. Sheets rise 24 pt and fade. No spring overshoot. Honor a reduced-motion state by cutting travel to a fade.

CHROME RULES
Default browser chrome:
- Top: a single rounded field, 48 pt, surface #141418, border #2E2E34. Left: lock or "not secure" in silver, never a colored badge. Center: host in pearl, title optional and truncated. Right: reload, which becomes stop while loading, and a media pill only when media exists.
- The field expands to an editor on tap: full URL or query, cancel, and a keyboard. Do not show a permanent URL bar plus a second search bar.
- A 2 pt progress line in #00DAC6 sits on the bottom edge of the top field while loading. No center spinner over the page except for the first 300 ms of a blank load.
- Bottom bar, 64 pt including gesture inset: back, forward, tabs (with a count), menu. Four items. Not home. Not downloads. Not settings. Inactive icons are #9AA0AE. Active is #F8F7F4. Disabled is 35 percent.
- On scroll down, the top field collapses to a 32 pt host line. It returns on scroll up. The bottom bar stays.
- The page area is a realistic article or a simple video page the user owns. Use a fictional host, "northline.example". Never show YouTube, Instagram, TikTok, or Netflix. Never put a "paste a video link" box on the start page.

MEDIA
When the page has exposed media, a pill appears in the top field: a small teal dot and "2". It does not cover the page. There is no draggable floating button in the default design. Tapping the pill opens a sheet, not a new screen.
Sheet anatomy, top to bottom: grabber, page title, host, then candidate rows. Each row: kind (video, audio, playlist), container (MP4, WEBM, M3U8), and a secondary line with resolution or "size unknown". No confidence percentage. No fake thumbnail. If a poster exists, one 16:9 image, 72 pt wide.
Selecting a playlist row refines into quality rows: 1080p, 720p, 480p, with bitrate if known. A primary button reads "Save". A resolving state is a quiet shimmer on the secondary line, and Save stays disabled until a row exists.
Failure sheet, same component: title "This media cannot be saved", body one of these exact sentences: "This stream is protected." / "Live streams cannot be saved." / "This playlist is encrypted." One button: "Close".

INFORMATION ARCHITECTURE — build these screens and only these
1. Browser, idle on the fictional article. Secure lock. No media pill.
2. Browser, loading, progress at 40 percent, field collapsing.
3. Browser, media detected, pill visible, page not obscured.
4. Address editor, keyboard open, query "northline field notes".
5. Quality sheet over a dimmed page, three rows, 720p selected.
6. Quality sheet, resolving.
7. Failure sheet, protected stream.
8. Tab switcher. Grid of six cards: host, title, close. One section "Private" with a violet keyline and two cards. New tab and new private tab as text buttons, not giant FABs.
9. Menu sheet from the bottom bar: bookmarks, history, downloads with badge 1, find in page, share, desktop site switch, settings. Nothing else.
10. Downloads. Three groups: active, completed, failed. Active row shows file name, host, a thin teal progress, "42 MB of 180 MB", pause. Completed row: open, share, delete. Failed row: the real reason and retry. Empty state: "No downloads yet" and no illustration of a person.
11. Bookmarks. List, search field, swipe or menu to delete. Empty state with "Save this page from the menu".
12. History, grouped by day. Private browsing leaves no rows. A clear action with a confirm dialog.
13. Settings, grouped: Browser, Downloads, Privacy, About. Every row is either a navigation, a switch that visibly changes the prototype, or a value. Include search engine, homepage, JavaScript, cookies, desktop site, Wi-Fi only, clear browsing data. Do not include a dark-theme switch. Do not include a download-folder browser; one row says "Saved in app storage" and "Export" opens a system-sheet mock.
14. Private tab. Same chrome, violet keyline on the top field, word "Private" in the field's secondary text. No other purple.
15. Player, opened from a completed download. Black page, 16:9 frame, play, scrubber, time, back to downloads. No editing tools.
16. About. Name, version 0.9.0, one-sentence description, GPL-3.0, privacy policy, source code. No social buttons.
17. Persian RTL mirror of screens 1, 5, and 10. Layout mirrors. Copy is Persian. Font is Vazirmatn. Do not leave English labels on those frames.

START PAGE
A new tab is not a portal. It is an empty field on charcoal, the wordmark "Salvia" in pearl at 22 pt, and six recent hosts as quiet rows. No wallpaper, no news feed, no ad, no "trending videos".

STATES TO INCLUDE ON THE DOWNLOADS AND BROWSER SCREENS
Loading, empty, error, offline. Offline is a single line above the page: "No connection". Do not replace the whole browser with an illustration.

ACCESSIBILITY
Contrast of #F8F7F4 on #0A0A0C and #C0C5CE on #141418 must stay at least WCAG AA. Do not put #00DAC6 text on white. Focus order in the prototype is visible. Labels are verbs: "Go back", "Show 2 media files", "Save 720p".

COPY RULES
Short, specific, no exclamation marks, no "Oops", no "unlock premium", no "powered by AI". The product has no account and no subscription. Do not design a paywall.

DELIVERABLES
1. The clickable phone prototype covering the 17 frames above, with working local navigation between them and static sample data.
2. A page in the prototype called "Spec" that lists the tokens, the type ramp, the chrome measurements, and the component names: AddressField, MediaPill, BottomBar, TabCard, MediaSheet, DownloadRow, SettingsRow, PlayerChrome.
3. Do not add screens I did not list. If something is missing, leave a note on the Spec page instead of inventing a feature.

SAMPLE COPY, ENGLISH
- Address placeholder: "Search or type a URL"
- Media pill: "2"
- Sheet title: "Save from this page"
- Save button: "Save"
- Downloads title: "Downloads"
- Active meta: "42 MB of 180 MB"
- Failed: "The connection dropped. The partial file was kept."
- Private: "Private"
- About body: "A fast, private browser that can save media a page already offers."

SAMPLE COPY, PERSIAN
- Address placeholder: "جست‌وجو یا نشانی"
- Sheet title: "ذخیره از این صفحه"
- Save button: "ذخیره"
- Downloads title: "دانلودها"
- Failed: "اتصال قطع شد. فایل ناقص نگه داشته شد."
- Private: "خصوصی"
- Empty downloads: "هنوز دانلودی نیست"
```

## Handoff prompt — after the prototype exists

Paste this into the Android agent, with screenshots or the Spec page attached. Do not attach the React source and ask for a translation.

```
Rebuild the attached SalviaBrowxer phone prototype in Jetpack Compose inside this repository. The prototype is a visual reference only. Do not port its React, CSS, or routing.

Follow docs/prompts/01-world-class-v1-execution-prompt.md for behavior, scope, and the order of work. Where the prototype and that document disagree, the execution prompt wins.

Use the existing Color.kt tokens. Map AddressField, MediaPill, BottomBar, TabCard, MediaSheet, DownloadRow, SettingsRow, and PlayerChrome onto the screens that document already requires. Measure against a real WebView, the IME, and gesture insets. Ship English and Persian. Do not add a floating download button. Do not add screens the execution prompt marks out of scope.
```
