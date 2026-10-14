# SalviaBrowxer Privacy Policy

> **Owner: replace the placeholders below before publishing.** Everything in `[square brackets]`
> must be filled in: legal name, contact email and effective date. Nothing else needs editing.

**Effective date:** [EFFECTIVE DATE]
**Applies to:** the SalviaBrowxer Android application (`com.salvia.salviabrowxer`)
**Data controller:** [LEGAL NAME]
**Contact:** [CONTACT EMAIL]

## Summary

SalviaBrowxer is a browser. It has no account system, no analytics, and no advertising. It sends no
data to the developer, and it collects nothing about you.

## What the app does with your data

- **Web pages.** When you open a page, the app requests it from that site exactly as any browser
  would. The site can see your IP address, your user-agent and the cookies it sets, under that
  site's own privacy policy. The developer of SalviaBrowxer never sees this traffic.
- **Downloads.** Files you download are written to the app's private storage on your device
  (`Android/data/com.salvia.salviabrowxer/files/Downloads`) and stay there until you delete or share
  them. Finished files are handed to Android's media scanner so they also appear in the system
  downloads view.
- **The session a download belongs to.** A file is fetched the way the page that offered it would
  fetch it: with the same cookies your WebView already holds for that site, the page as a `Referer`
  (the full address to that site, only the origin to a different one) and the same user-agent the
  page was loaded with. Your cookie store is only ever read — a download can never change it, add to
  it or sign you out. This is what lets media that only plays while you are signed in download
  successfully instead of being refused by the site's own server.
- **Local history and bookmarks.** Download queue, bookmarks and browsing history are stored in a
  local database on your device. Each download row also remembers the page and user-agent it was
  started with, so a queued transfer can resume with the session it belongs to. Private tabs are not
  written to history.
- **Private tabs.** A private tab is *not* a separate browsing identity, and this policy says so
  plainly: the app does not keep a second cookie jar, so private and normal tabs share one WebView
  profile. Cookies, site storage, cache and any site login therefore carry over between them, and a
  site you signed into in a normal tab still recognises you in a private tab. What a private tab does
  is narrower, and it does it exactly: its visits are never written to history, and private tabs are
  never saved to the session, so closing the app leaves no list of them behind. Files you download
  from a private tab are saved like any other download and remain on the device.
- **Settings.** Your preferences are stored on your device only.
- **Ad and tracker blocking (optional, off by default).** When you turn it on, the app answers
  requests to a short list of known advertising and tracking hosts with an empty response instead of
  contacting them. That list is bundled with the app — nothing is downloaded to update it, no URL
  you visit is sent anywhere for a decision, and the counters (requests blocked, sites you exempted)
  are stored in your own settings on the device. The feature is off until you enable it.
- **Network requests.** The app makes a network request only to load a page you opened, to probe
  media the page already exposed, or to download something you started. A request blocked by the
  optional ad blocker is not sent at all. There is no background
  telemetry, no crash-reporting service, and no third-party SDK that phones home.

## What the app does not do

- No account and no sign-in.
- No analytics, attribution or advertising SDKs.
- No collection of device identifiers, location, contacts or files.
- No upload of your browsing history, bookmarks, downloads or settings.
- No sale or sharing of personal data, because none is collected.

## Permissions and why they exist

| Permission | Why |
| --- | --- |
| `INTERNET` | Load pages and download files you request |
| `ACCESS_NETWORK_STATE` | Wi-Fi-only mode: hold transfers off Wi-Fi |
| `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Keep a download running while the app is in the background, with a visible notification |
| `POST_NOTIFICATIONS` (Android 13+) | Show download progress; denying it still lets downloads finish |

The app requests no storage permission. It does not read files it did not create.

## Backups

Android cloud backup and device-to-device transfer exclude the app's database, its preferences file,
the download folder and the WebView cookie/cache directories. Your browsing data does not leave the
device through backup.

## Children

The app is a general-purpose browser and is not directed at children. It does not collect personal
data from anyone.

## Your choices

- Delete individual downloads, bookmarks or the whole history from inside the app.
- Clear cookies and site data from Settings.
- Uninstall the app to remove everything it stored on the device.

## Changes

If this policy changes, the effective date above is updated and the new version is published at the
same URL.

## Contact

Questions about this policy: [CONTACT EMAIL].

## Local storage added by the download settings

Two things the download settings may store, both on the device and neither sent anywhere:

- **a password verifier** for the locked folder — a PBKDF2 derivation of the password (120,000
  iterations, a random salt), never the password itself. It is kept in a preference file encrypted
  with a key held by the device keystore; if that keystore cannot be opened, it is kept in the app's
  private preferences instead and the settings screen says which of the two happened
- **a folder reference** — the storage-access-framework tree the user picked for a second copy of
  each finished download, as a URI plus the permission Android granted through its own picker

Removing the password deletes the verifier, unlocks every file it was hiding, and takes no copy of
anything.
