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
- **Local history and bookmarks.** Download queue, bookmarks and browsing history are stored in a
  local database on your device. Private tabs are not written to history.
- **Settings.** Your preferences are stored on your device only.
- **Network requests.** The app makes a network request only to load a page you opened, to probe
  media the page already exposed, or to download something you started. There is no background
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
