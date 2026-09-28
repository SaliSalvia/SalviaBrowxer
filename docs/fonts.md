# Fonts

SalviaBrowxer ships two typefaces, both under the SIL Open Font License 1.1. They are bundled
under `app/src/main/res/font/` rather than fetched at runtime, so the UI never falls back to a
system face and never needs a network call or Play Services to render.

| Locale | Family | Files | Weights |
| --- | --- | --- | --- |
| every locale except Persian | **Inter** | `inter_regular.ttf`, `inter_medium.ttf`, `inter_bold.ttf` | 400 / 500 / 700 |
| Persian (`fa`) | **Vazirmatn** | `vazirmatn_regular.ttf`, `vazirmatn_medium.ttf`, `vazirmatn_bold.ttf` | 400 / 500 / 700 |

Only the three weights the `Typography` scale actually asks for are shipped. Requesting a weight
that is not bundled would make the platform synthesise it, which is why `Type.kt` and `AppFonts`
stay in sync: `Normal`, `Medium` and `Bold` are the only weights used.

Vazirmatn is used for Persian because Inter has no Arabic-script coverage — an Arabic-script
locale rendered in Inter would silently fall back to the system face and lose the brand's
typography. A Latin face is never used for Persian text.

## How the face is chosen

`AppFonts.usesPersianFace` reads the language tag from the current `Configuration` and picks
Vazirmatn when it is Persian, Inter otherwise. There is no in-app font switch: the face follows
the app language, and the app language follows the system (or, on Android 13+, the per-app
language picker declared in `res/xml/locales_config.xml`).

## Licenses

- Inter — Copyright (c) 2016 The Inter Project Authors — [`docs/licenses/Inter-OFL.txt`](licenses/Inter-OFL.txt)
- Vazirmatn — Copyright 2015 The Vazirmatn Project Authors — [`docs/licenses/Vazirmatn-OFL.txt`](licenses/Vazirmatn-OFL.txt)

Both files carry the full SIL Open Font License 1.1 text. The reserved font names are not used
for anything other than the original files here: the fonts are redistributed unmodified, under
their original file names' casing and with their copyright notices intact.

Sources:

- https://github.com/rsms/inter (release v4.1, static `extras/ttf`)
- https://github.com/rastikerdar/vazirmatn (release v33.003, static `fonts/ttf`)
