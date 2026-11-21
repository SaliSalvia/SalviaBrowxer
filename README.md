# SalviaBrowxser

مرورگر اندرویدی امن، سریع و فوق‌حرفه‌ای مبتنی بر موتور کرومیوم (Chromium WebView) با رابط کاربری کیهانی تیره، مسدودکننده تبلیغات و ردیاب‌ها، حالت ناشناس (Incognito)، پشتیبانی از افزونه‌ها به سبک فایرفاکس، ابزارهای پیشرفته توسعه‌دهندگان (DevTools) و حباب شناور مغناطیسی تشخیص و دانلود مدیا.

---

## ویژگی‌های اصلی

- **موتور کرومیوم با سازگاری کامل ورود (Sign-In)**: تنظیم User-Agent استاندارد کروم (بدون توکن محدودکننده `wv`) و پشتیبانی از کوکی‌ها و پنجره‌های بازشوی OAuth برای ورود بدون خطا به Gmail، حساب گوگل و تمامی وب‌سایت‌ها.
- **موتور تشخیص و دانلود مدیای پیشرفته + حباب شناور مغناطیسی (سطح InShot Video Downloader)**:
  - حباب با لمس طولانی قابل جابجایی است و هنگام رها شدن به صورت مغناطیسی به نزدیک‌ترین لبه صفحه می‌چسبد.
  - تزریق هوک زودهنگام `fetch()` / `XMLHttpRequest` / `JSON-LD` برای شناسایی ویدیوها حتی در پلیرهای `blob:` (MSE) و پشتیبانی از استریم‌های **MP4، HLS (`.m3u8` با رمزگشایی AES-128 و ترک صدای مجزا)، و MPEG-DASH (`.mpd`)**.
  - تبدیل بومی استریم‌های HLS/DASH به فایل استاندارد **MP4** با `MediaExtractor` + `MediaMuxer` اندروید.
  - دانلودر چندرشته‌ای (۴ پارت هم‌زمان با HTTP `Range`) به همراه **Pause / Resume واقعی**، نمایش سرعت لحظه‌ای و حذف خودکار کلیپ‌های تبلیغاتی پیش از ویدیو (مانند تبلیغات ۵ ثانیه‌ای) و اولویت‌بندی ویدیوی اصلی صفحه.
- **سپر ضدتبلیغ چندلایه به سبک مرورگر Aloha**:
  - پشتیبانی از سینتکس **EasyList / AdBlock Plus** (`||domain^`، `@@||domain^`، `##selector` و وایلدکارد `*`)، مسدودسازی اسکریپت‌های ماینر ارز دیجیتال (Crypto-miners)، و مسدودسازی ریدایرکت‌ها و پاپ‌آپ‌های تبلیغاتی (`window.open` و `shouldOverrideUrlLoading`).
  - پاک‌سازی زنده DOM با `MutationObserver` و حذف لایه‌های شفاف کلیک‌دزدی (Clickjacking overlays).
  - کلید توقف/فعال‌سازی سپر برای هر سایت به‌صورت مجزا در منوی امنیت آدرس‌بار، ضمن حفظ لیست سفید ورود به Gmail و OAuth.
- **سیستم افزونه‌ها به سبک فایرفاکس (WebExtensions / UserScripts)**:
  - شامل ۶ افزونه پیش‌فرض (*Dark Reader Pro*، *uBlock Script Shield*، *Video Ad Auto-Skipper*، *Force Copy*، *Video Speed Controller*، *JSON Formatter Pro*) و امکان ساخت افزونه سفارشی با JS و CSS.
- **ابزارهای توسعه‌دهندگان (DevTools)**:
  - کنسول جاوااسکریپت با REPL تعاملی، مانیتور شبکه (Network)، بازرس HTML DOM و متا تگ‌ها، و نمایشگر کوکی‌ها و `localStorage` / `sessionStorage`.

---

## ساخت خودکار APK در GitHub Actions

این مخزن دارای ورکفلو آماده در مسیر `.github/workflows/build-apk.yml` است که با هر `push` به شاخه‌های `main` یا `master`، اجرای دستی (`workflow_dispatch`)، یا ثبت تگ نسخه (`v1.0.0`) به صورت خودکار:

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

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

فایل خروجی در مسیر `app/build/outputs/apk/debug/app-debug.apk` تولید می‌شود.
