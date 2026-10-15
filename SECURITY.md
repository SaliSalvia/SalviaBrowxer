# Security Policy

SalviaBrowxer is a privacy-first Android browser. Its security surface is small on purpose — no
analytics, no accounts, no remote list fetching — and reports about that surface are welcome.

## Reporting a vulnerability

Please report vulnerabilities privately:

1. Open a **GitHub Security Advisory** on this repository
   (Report a vulnerability → Security tab), **or**
2. Open a private security advisory draft and reference `docs/analysis-fa.md` / the affected file.

Please **do not open a public issue** for anything that could put users at risk.

Include what you can of:

- the affected file or screen (this repository has no obfuscated build: file names are real),
- the Android/WebView versions involved,
- a minimal page or intent that reproduces the problem.

## Scope of particular interest

- The JavaScript bridges (`BlobDownloadBridge`, `MediaSnifferBridge`, the MSE recording port):
  what a hostile page can claim, inject or stage through them.
- Download filename and content-type handling, and the FileProvider surface
  (`file_paths.xml` is deliberately narrow).
- The locked folder's verifier and its `EncryptedSharedPreferences` store.
- Session handling: cookies, `Referer` carrying, backup/d2d exclusion rules.

## Out of scope

- Bypassing DRM or site protections. The app deliberately does not contain site-specific
  extractors, token harvesting or DRM circumvention, and reports asking for them are not
  vulnerabilities.
- Attacks requiring physical access to an unlocked device.

## Expectations

- A report is acknowledged within 7 days.
- Fixes ship in the next release when possible; the advisory is published with it.
- Credit is given unless you prefer otherwise.
