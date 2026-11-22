# SalviaBrowxser

مرورگر اندرویدی امن، سریع و فوق‌حرفه‌ای مبتنی بر موتور کرومیوم (Chromium WebView) با رابط کاربری کیهانی تیره، مسدودکننده تبلیغات و ردیاب‌ها، حالت ناشناس (Incognito)، پشتیبانی از افزونه‌ها به سبک فایرفاکس، ابزارهای پیشرفته توسعه‌دهندگان (DevTools) و حباب شناور مغناطیسی تشخیص و دانلود مدیا.

---

## ویژگی‌های اصلی

- **موتور کرومیوم با سازگاری کامل ورود (Sign-In)**: تنظیم User-Agent استاندارد کروم (بدون توکن محدودکننده `wv`) و پشتیبانی از کوکی‌ها و پنجره‌های بازشوی OAuth برای ورود بدون خطا به Gmail، حساب گوگل و تمامی وب‌سایت‌ها.
- **موتور تشخیص و دانلود مدیای پیشرفته + حباب شناور مغناطیسی (سطح InShot Video Downloader)**:
  - حباب با لمس طولانی قابل جابجایی است و هنگام رها شدن به صورت مغناطیسی به نزدیک‌ترین لبه صفحه می‌چسبد.
  - تشخیص URLهای مستقیم مدیا با هوک‌های زودهنگام `fetch()` و `XMLHttpRequest`، اسکن عناصر HTML5، داده‌های `JSON-LD` و `OpenGraph`؛ پردازش manifestهای **HLS (`.m3u8`)** و **MPEG-DASH (`.mpd`)** نیز حفظ شده است.
  - ارجاع‌های داخلی پلیرهای `blob:` / MSE و جریان‌های `MediaStream` بدون منبع مستقیم HTTP(S)، به‌صورت «مرجع غیرقابل دانلود» نمایش داده می‌شوند؛ برنامه بافر MSE را بازسازی نمی‌کند و محدودیت‌های DRM را دور نمی‌زند. اگر یک URL مستقیم HTTP(S) جداگانه شناسایی شود، همان منبع مستقل می‌تواند در فهرست دانلود نمایش داده شود.
  - پشتیبانی موجود از استریم‌های **MP4، HLS (با رمزگشایی AES-128 و ترک صدای مجزا)، و MPEG-DASH** حفظ شده است؛ تبدیل بومی HLS/DASH به فایل استاندارد **MP4** از طریق `MediaExtractor` و `MediaMuxer` اندروید انجام می‌شود.
  - دانلودر چندرشته‌ای (۴ پارت هم‌زمان با HTTP `Range`) به همراه **Pause / Resume واقعی**، نمایش سرعت لحظه‌ای و حذف خودکار کلیپ‌های تبلیغاتی پیش از ویدیو (مانند تبلیغات ۵ ثانیه‌ای) و اولویت‌بندی ویدیوی اصلی صفحه.
- **سپر ضدتبلیغ چندلایه به سبک مرورگر Aloha**:
  - پشتیبانی از سینتکس **EasyList / AdBlock Plus** (`||domain^`، `@@||domain^`، `##selector` و وایلدکارد `*`)، مسدودسازی اسکریپت‌های ماینر ارز دیجیتال (Crypto-miners)، و مسدودسازی ریدایرکت‌ها و پاپ‌آپ‌های تبلیغاتی (`window.open` و `shouldOverrideUrlLoading`).
  - پاک‌سازی زنده DOM با `MutationObserver` و حذف لایه‌های شفاف کلیک‌دزدی (Clickjacking overlays).
  - کلید توقف/فعال‌سازی سپر برای هر سایت به‌صورت مجزا در منوی امنیت آدرس‌بار، ضمن حفظ لیست سفید ورود به Gmail و OAuth.
- **بهینه‌سازی مصرف رم و کارایی (Tab Hibernation & Zero-GC Buffers)**:
  - توقف خودکار تب‌های پس‌زمینه (`onPause`) و خواب زمستانی هوشمند تب‌های کم‌کاربرد (ذخیره وضعیت ناوبری و آزادسازی موتور Chromium WebView برای نگهداشت حداکثر ۲ تا ۳ تب فعال در رم و بازیابی فوری هنگام بازگشت) به همراه واکنش به رویداد `onTrimMemory`.
  - بافرگذاری سبک لاگ‌های شبکه و کنسول و تزریق رویدادمحور (`MutationObserver`) برای پیمایش روان و مصرف حداقل CPU و حافظه.
- **سیستم افزونه‌ها به سبک فایرفاکس (WebExtensions / UserScripts)**:
  - شامل ۶ افزونه پیش‌فرض (*Dark Reader Pro*، *uBlock Script Shield*، *Video Ad Auto-Skipper*، *Force Copy*، *Video Speed Controller*، *JSON Formatter Pro*) و امکان ساخت افزونه سفارشی با JS و CSS.
- **ابزارهای توسعه‌دهندگان (DevTools)**:
  - کنسول جاوااسکریپت با REPL تعاملی، مانیتور شبکه (Network)، بازرس HTML DOM و متا تگ‌ها، و نمایشگر کوکی‌ها و `localStorage` / `sessionStorage`.

---

## ساخت خودکار APK در GitHub Actions

این مخزن دارای ورکفلو آماده در مسیر `.github/workflows/build-apk.yml` است که با هر `push` به شاخه‌های `main` یا `master`، اجرای دستی (`workflow_dispatch`)، یا ثبت تگ نسخه (`v1.0.0`) به صورت خودکار روی **JDK 21** (همراه با Android SDK Platform 36.1 و Build-Tools 36.1.0) اجرا می‌شود:

1. تست‌های واحد (`:app:testDebugUnitTest`) را اجرا می‌کند.
2. فایل نصب اندروید (`SalviaBrowxser-debug.apk`) را می‌سازد و در بخش **Actions → Artifacts** مخزن برای دانلود قرار می‌دهد.
3. در صورت انتشار تگ (مانند `v1.0.0`)، یک **GitHub Release** خودکار همراه با فایل APK ضمیمه‌شده منتشر می‌کند.

### ساخت نسخه Release امضاشده (اختیاری)
در صورت تمایل به ساخت خودکار `SalviaBrowxser-release.apk` در ورکفلو، سه Secret زیر را در **Settings → Secrets and variables → Actions** مخزن گیت‌هاب خود تعریف کنید:
- `RELEASE_KEYSTORE_BASE64`: خروجی Base64 فایل keystore امضا
- `STORE_PASSWORD`: رمز keystore
- `KEY_PASSWORD`: رمز کلید (`upload`)

---

## ساخت محلی (Local Build)

### پیش‌نیازها
- **JDK 21**: تست‌های واحد با Robolectric روی Android SDK 36 اجرا می‌شوند و این سندباکس به Java 21 نیاز دارد (Gradle 9.3.1 و AGP 9.1.1 نیز روی JDK 21 اجرا می‌شوند).
- **Android SDK**: `platforms;android-36.1` و `build-tools;36.1.0`
- **Gradle 9.3.1**

پیکربندی امضای `debugConfig` فایل `debug.keystore` را در ریشه پروژه می‌خواند؛ پیش از اولین ساخت آن را بسازید:

```bash
keytool -genkey -v -keystore debug.keystore -storepass android \
  -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

سپس تست‌ها و فایل نصب را بسازید:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

فایل خروجی در مسیر `app/build/outputs/apk/debug/app-debug.apk` تولید می‌شود.

برای ساخت نسخه Release امضاشده، فایل `my-upload-key.jks` را با کلید (`alias`) به نام `upload` در ریشه پروژه قرار دهید و رمزها را به‌صورت متغیر محیطی تنظیم کنید:

```bash
STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:assembleRelease
```

خروجی: `app/build/outputs/apk/release/app-release.apk`
