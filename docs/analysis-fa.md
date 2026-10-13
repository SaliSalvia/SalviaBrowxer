# تحلیل فنی SalviaBrowxer

> این سند یک **بازبینی ایستا (static review)** است، نه گزارش پذیرش انتشار. هیچ بخشی از کد در این
> محیط کامپایل یا اجرا نشد (JDK و Android SDK نصب نیست و دسترسی شبکه به مخازن Google/Maven وجود
> ندارد)، پس هر ادعای «کار می‌کند» در README با خواندن کد بررسی شده و نه با اجرا. هرجا مطمئن نیستم،
> صریح گفته شده است.

---

## ۱) حکم کوتاه

این پروژه از نظر **معماری و انسجام طراحی** به‌طور غیرمعمولی بالغ است: یک منبع حقیقت برای قواعد
رسانه، پذیرش مبتنی بر شاهد، و امتناع‌های صادقانه. مشکل اصلی، شکاف میان **ادعا و پوشش تست** در چند
نقطهٔ مشخص است، نه سردرگمی معماری. سه دستهٔ مسئله واقعی وجود دارد:

1. **دو موضوع که مستقیماً وعدهٔ محصول را می‌شکنند**: هر بار ترک صفحهٔ مرورگر (Downloads/Settings/…)
   همهٔ WebViewها را نابود و صفحه را از نو لود می‌کند، و دانلود رسانه‌هایی که سمت سرور با
   کوکی/Referer محافظت می‌شوند شکست می‌خورد.
2. **یک ناحیهٔ ریسک در سرویس دانلود**: مسابقهٔ `stopWhenIdle` و throttle مشترک پیشرفت.
3. **بدهی نگهداشت**: سند وضعیت کهنه که با کد در تناقض است، ثابت‌های مرده، کد بی‌استفاده، و یک فایل
   APK ۱۹ مگابایتی که در گیت کامیت شده.

---

## ۲) تصویر کلی

| سنجه | مقدار |
| --- | --- |
| فایل‌های Kotlin (کد اصلی) | ۹۱ فایل / ۱۲٬۷۶۶ خط |
| فایل‌های تست | ۳۰ فایل / ۴٬۱۶۷ خط |
| ماژول‌ها | ۶ (`:app`, `:core:model`, `:core:database`, `:media:detector`, `:media:resolver`, `:media:downloader`) |
| منابع | en/fa هر دو ۲۰۸ رشته، **مجموعه کلیدها ۱۰۰٪ یکسان** (بدون رشتهٔ گمشده در فارسی) |
| نسخه | `versionCode 2` / `1.0.0`، `minSdk 24`، `compileSdk`/`targetSdk 36` |
| تاریخ گیت | **تک‌کامیت squash‌شده** — کل تاریخ پروژه در یک کامیت؛ bisect و «چه تغییری این را شکست» ممکن نیست |

قاعدهٔ اعلام‌شدهٔ «یک ماژول فقط وقتی وجود دارد که API عمومی و مصرف‌کنندهٔ بیرونی داشته باشد» واقعاً رعایت
شده است: هر شش ماژول مصرف‌کننده دارند و هیچ ماژول تشریفاتی ساخته نشده.

---

## ۳) نقاط قوت واقعی (با شاهد کد)

- **یک منبع حقیقت برای «رسانه چیست».** `MediaUrlRules` تنها جایی است که امتدادها/MIMEها را می‌داند و
  `EXTENSION_ALTERNATION` را داخل جاوااسکریپت تزریق می‌کند (`MediaSniffer.script`،
  `MEDIA_DETECTION_JS`). این همان اشتباهی است که قبلاً پنج نسخهٔ ناسازگار داشت و `site.webmanifest`
  را به‌عنوان پلی‌لیست پیشنهاد می‌داد؛ حالا رفع شده و کامنت‌ها هم *چرایی* را ثبت کرده‌اند.
- **پذیرش مبتنی بر شاهد، نه حدس.** `MediaSniffAdmission` سه سطح شاهد (هدر پاسخ سرور / پلیر که واقعاً
  بارگذاری کرده / ماکاپ) را از هم جدا می‌کند، بازهٔ جزئی (`Range` غیر از `bytes=0-`)، سگمنت‌ها و
  MIMEهای ضعیف را رد می‌کند. این دقیقاً همان چیزی است که یک اسنیفر را از «۲۰۰ ردیف زباله در سبد»
  نجات می‌دهد.
- **امتناع صادقانه.** `MediaOfferability` + `BrowserViewModel.unsupportedReasonFor` مسیر واحدی دارند:
  DASH غیرقابل پارس، live، و blob مبتنی بر MSE با پیام روشن رد می‌شوند، و لایهٔ دانلود هم دوباره
  بررسی می‌کند (`enqueueDownload`) تا مسیر فراموش‌شده نتواند به صف برسد.
- **حریم خصوصی جدی گرفته شده.** `allowFileAccess=false`، `allowUniversalAccessFromFileURLs=false`،
  ژئولاکیشن خاموش، `javaScriptCanOpenWindowsAutomatically=false`، Safe Browsing روشن (API 26+)،
  بدون هیچ SDK تحلیلی/تبلیغاتی، و حذف سیستماتیک دادهٔ مرور از backup و device-transfer
  (`backup_rules.xml`، `data_extraction_rules.xml`) شامل خود `app_webview`.
- **FileProvider باریک است.** فقط `Downloads/`، `Movies/`، `Music/`، `Pictures/`، `Documents/` و
  `cacheDir/blob` در `file_paths.xml` باز است — نه ریشهٔ پوشه‌ها.
- **تست در سه لایهٔ درست.** `MockWebServer` واقعی برای HLS/DASH (رمزگشایی AES-128، چرخش کلید،
  `EXT-X-BYTERANGE`، استفادهٔ دوباره از قطعات staged، رد `SAMPLE-AES`)، Robolectric برای انتشار در
  MediaStore روی API 24 و 33، و تست ابزاری روی دستگاه برای JCE خود اندروید.
- **دو زبان واقعاً کامل** (نه فقط رشته‌های خالی) و استفاده از `AutoMirrored` برای آیکون‌های جهت‌دار.

---

## ۴) یافته‌ها به ترتیب شدت

### P0-1 — با ترک صفحهٔ مرورگر (نه فقط چرخش) همهٔ WebViewها نابود و صفحه دوباره لود می‌شود

`BrowserScreen.kt:121` انبار را با `remember` می‌سازد و `BrowserScreen.kt:466-472` عمداً هنگام
خروج از ترکیب‌بندی آن را پاک می‌کند:

```kotlin
val store = remember(context, viewModel) { TabWebViewStore(...) }
...
DisposableEffect(store) {
    onDispose {
        // Leaving the browser releases the WebViews. The view model keeps every tab's URL
        // and title, so coming back restores the current tab from its last committed URL.
        store.destroyAll()
    }
}
```

یعنی این یک باگ اتفاقی نیست، یک **تصمیم طراحی مستندشده** است — اما هزینه‌اش دیده نشده:

- «ترک مرورگر» فقط رفتن به اپ دیگری نیست. در Navigation Compose وقتی مقصد عوض می‌شود، ترکیب‌بندی
  صفحهٔ قبلی از بین می‌رود. پس **هر بار** که کاربر روی Downloads، Settings، Bookmarks، History یا
  Player بزند، هر ۸ WebView نابود می‌شوند. با برگشت، `webViewFor(tabId, initialUrl)` یک WebView
  تازه می‌سازد و `view.loadUrl(initialUrl)` صفحه را **از نو از شبکه** می‌آورد.
- نتیجه برای کاربر: صفحه‌ای که وسط فرم، وسط ویدیو یا وسط اسکرول بوده دوباره لود می‌شود؛ پشتهٔ
  back/forward و اسکرول هر تب از بین می‌رود؛ پخش درون‌صفحه‌ای قطع می‌شود. این با جملهٔ README («کتابخانه
  در همان تب باز می‌شود» و توضیح «تب‌های زنده هرگز reload نمی‌شوند») در تضاد عملی است، هرچند تضمین
  `TabWebViewStore` برای *تعویض تب در همان صفحه* درست کار می‌کند.
- چرخش گوشی هم دقیقاً همین مسیر را می‌رود (`android:configChanges` در `AndroidManifest.xml:53-56`
  تعریف نشده)، پس همان ریلود و همان از دست رفتن وضعیت دوباره رخ می‌دهد.
- مزیت درست این طراحی: نشتی حافظه ندارد (مسیر `onDispose` واقعاً `destroyAll()` را صدا می‌زند) و
  مصرف رم را با سقف ۸ WebView نگه می‌دارد.

**اقدام:** مالکیت انبار را از ترکیب‌بندی به `BrowserViewModel` (یا یک ViewModel اختصاصی با `onCleared` →
`destroyAll`) منتقل کن تا ناوبری داخل‌برنامه‌ای و چرخش، تب‌ها را نکشد. اگر هدف محدودکردن حافظه است،
به‌جای «همه را نابود کن»، LRU موجود (`maxLiveTabs = 8`) را با `ComponentCallbacks2.onTrimMemory` ترکیب
کن و انبار را دست‌کم هنگام چرخش زنده نگه دار. اگر عمداً همین رفتار می‌خواهی (سادگی + حافظهٔ کم)، مانیفست
باید `configChanges` بگیرد و متن UI/README هم باید صریح بگوید که بازگشت از صفحه‌های کتابخانه صفحه را
دوباره لود می‌کند — وگرنه کاربر آن را «مرورگر بی‌حافظه» می‌بیند.

### P0-2 — دانلود رسانه‌های محافظت‌شده با کوکی/Referer شکست می‌خورد

کلاینت OkHttp هیچ `cookieJar` ندارد و به `CookieManager` اندروید وصل نیست (`di/AppModule.kt:41-52`)،
و هیچ‌کدام از مسیرهای دانلود هدر `Referer` نمی‌فرستند:

- `DirectMediaResolver.resolve` فقط `User-Agent` می‌فرستد (`media/resolver/.../DirectMediaResolver.kt:23`)
- `DownloadManager.transfer` فقط UA و `Accept` (`media/downloader/.../DownloadManager.kt:106-110`).
- لوله‌کشی Referer **وجود دارد ولی هرگز وصل نمی‌شود**: `FileDownload.referer` تعریف شده و
  `DownloadManager`، `HlsDownloader` و `DashDownloader` هر سه آن را محترم می‌شمارند، اما هر دو نقطهٔ
  ساخت `FileDownload` در `DownloadService.kt:304,318` بدون `referer` ساخته می‌شوند و `DownloadEntity`
  هم ستون `pageUrl`/`referer` ندارد — پس حتی بعد از مرگ پروسه چیزی برای پرکردنش ذخیره نشده است.
  (پلی‌لیست رمزگشایی‌شدهٔ AES هم معمولاً بدون Referer/کوکی `403` می‌شود.)

پیامد عملی: سایتی که رسانه را در WebView پخش می‌کند (با کوکی نشست) می‌تواند همان URL را در دانلود
`403` کند. این دقیقاً نقطه‌ای است که وعدهٔ مرکزی محصول («وقتی صفحه فایلی دارد، مرورگر آن را با
اطمینان ذخیره می‌کند») می‌شکند — و اسنیفر اینجا درست کار کرده، تقصیر لایهٔ دانلود است. در README و
roadmap هم به‌عنوان «Still open» ثبت شده («Confirm requests do not falsely promise access to
cookies/authenticated pages») اما در کد هیچ ردی از آن نیست.

**اقدام:** یک `CookieJar` پل به `android.webkit.CookieManager` (با `flush()` همگام) و ثبت `pageUrl` به
عنوان Referer هنگام enqueue. صف Room باید `referer` را هم نگه دارد، وگرنه از سرگیری پس از مرگ
پروسه هدر را از دست می‌دهد. اگر تصمیم آگاهانه «بدون کوکی» است، باید در UI همان لحظه گفته شود، نه در
قالب خطای `HTTP 403`.

### P0-3 — دو مشکل همزمانی در `DownloadService`

الف) **مسابقهٔ `stopWhenIdle`.** در `startQueueProcessor` شناسه از `queue.receive()` برداشته می‌شود و
کار در یک `launch` جدا شروع می‌شود، اما `inFlight` فقط *بعد از* چند نقطهٔ تعلیق (خواندن Room، بررسی
Wi‑Fi) افزایش می‌یابد (`DownloadService.kt:170-210`). در همان بازه، `finally` یک دانلود دیگر شرط
`inFlight.get() == 0 && queue.isEmpty` را درست می‌بیند (`DownloadService.kt:184`) و `stopSelf()` را صدا
می‌زند → `onDestroy` → `scope.cancel()` → همان دانلودی که تازه شروع شده بود وسط راه می‌میرد و ردیفش
در وضعیت `QUEUED/DOWNLOADING` جا می‌ماند. این دقیقاً سناریوی «کاربر دو تا دانلود پشت‌سرهم می‌زند»
است. جبرانِ ناقص: `recoverQueue()` فقط در `onStartCommand` بعدی اجرا می‌شود، و `DownloadsViewModel.init`
هم `processQueue` را صدا می‌زند — یعنی بهبود بدون تعامل کاربر.

ب) **throttle مشترک پیشرفت.** `lastPersistTime` و `lastPersistJob` *یک* نمونه برای همهٔ دانلودهای
همزمان‌اند (`DownloadService.kt:79-80, 457-470`). با سقف ۳ دانلود موازی، رویداد پیشرفت دانلود A می‌تواند
کار زمان‌بندی‌شدهٔ B را کنسل کند و برعکس؛ نتیجه، پرش‌های نامنظم درصد/سرعت/ETA در UI است. کلید باید
per-download باشد (مثلاً `ConcurrentHashMap<String, Long>` + یک Job برای هر شناسه).

ج) **نشانگر Wi‑Fi با رشتهٔ محلی‌شده مقایسه می‌شود.** `wifiHoldMarker` از
`getString(R.string.download_waiting_for_wifi)` می‌آید و بعد با `==` روی مقدار ذخیره‌شده در دیتابیس
تطبیق داده می‌شود (`DownloadService.kt:88, 133, 141, 198`). با تغییر زبان برنامه، مقدار ذخیره‌شدهٔ در
پایگاه‌داده دیگر برابر نمی‌شود و آن دانلود برای همیشه «در انتظار Wi‑Fi» می‌ماند و هرگز بازگردانی
نمی‌شود. شناسهٔ پایدار (enum/مقدار بدون ترجمه) لازم است.

### P0-4 — WebView بدون مدیریت خطا و بدون مدیریت سقوط رندرر

`ui/utils/WebViewClientWrapper.kt` هیچ‌کدام را override نمی‌کند:

- `onRenderProcessGone` — سقوط رندرر (روی صفحات سنگین و با ۸ تب زنده کاملاً محتمل) به‌طور پیش‌فرض
  **کل فرآیند برنامه را می‌کشد**. مرورگری که با یک صفحهٔ بد بسته می‌شود، همان چیزی است که بازخورد
  یک‌ستاره می‌سازد.
- `onReceivedError` / `onReceivedHttpError` / `onReceivedSslError` — خطای بارگذاری هیچ نماینده‌ای در UI
  ندارد؛ کاربر اسپینر می‌بیند که در ۱۰۰٪ می‌ماند یا صفحهٔ سفید. (به‌نفع انصاف: WebView امروزی برای
  خطاهای شبکه صفحهٔ پیش‌فرض خودش را نشان می‌دهد، اما برای خطای SSL به‌طور پیش‌فرض قطع می‌کند و توضیحی
  نمی‌دهد.)
- `setDownloadListener` هم وجود ندارد: کلیک روی یک فایل غیررسانه‌ای (ZIP/PDF) در مرورگر **هیچ کاری
  نمی‌کند**. شاید آگاهانه باشد («رسانه را سبد می‌گیرد»)، اما یک مرورگر عمومی نمی‌تواند بی‌صدا باشد؛
  حداقل یک پیام «این نوع فایل پشتیبانی نمی‌شود» لازم است.

### P1-1 — سطح حملهٔ پل‌های جاوااسکریپت

هر دو پل با `addJavascriptInterface` به **هر صفحه و هر iframe** (شامل سکوی تبلیغات) در معرض است، و
`addDocumentStartJavaScript(view, script, setOf("*"))` عمداً روی همهٔ originها تزریق می‌کند.

- `BlobDownloadBridge.onBlobData` و `onBlobChunk` را **هر صفحه‌ای می‌تواند مستقیم صدا بزند**، بدون
  اینکه کاربر دانلودی را انتخاب کرده باشد. سقف‌ها فقط روی *اندازهٔ هر تماس* گذاشته شده‌اند
  (`160MB` رشتهٔ base64، `90MB` بایت رمزگشایی‌شده)، نه روی *تعداد*: نگاشت `pending` هیچ سقفی، هیچ
  TTL و هیچ پاک‌سازی‌ای ندارد، پس یک صفحه می‌تواند هزاران ورودی نیمه‌کاره بسازد. هر نوشتن هم بدون
  محدودیت کل یا تعداد در `cacheDir/blob` می‌نشیند (دیسک‌خوری). برای مقایسه، پذیرش محافظه‌کارانه‌ای که
  برای sniffer نوشته شده، روی این پل وجود ندارد.
- `MediaSnifferBridge` هرچند طول‌ها را می‌بندد و `MediaSniffAdmission` را اعمال می‌کند، اما «pageUrl» را
  خود صفحه اعلام می‌کند؛ صفحهٔ مهاجم می‌تواند برای تب دیگری کاندید تزریق کند. برای مرورگری که هشدار
  «On screen» می‌دهد، تزریق کاندید توسط iframe تبلیغاتی یک مسئلهٔ قابل توجه است.

**اقدام کم‌هزینه:** سقف تعداد ورودی‌های `pending` + منقضی‌کردن بعد از N ثانیه + سقف مجموع بایت‌های
staged + بررسی `pageUrl` بر ضد URL فعلی همان WebView + گیت‌کردن مسیرهای «استخراج واقعی» روی انجام
شدن یک اقدام کاربر (فلگی که فقط `FetchBlob` ست می‌کند). در `shouldOverrideUrlLoading` هم
`request.hasGesture()` و `request.isForMainFrame` بررسی شود: الان یک iframe می‌تواند با
`location = "intent://…"` یک اپ بیرونی را باز کند (`WebViewClientWrapper.kt:31-35`).

### P1-2 — کپی گالری، مالکیت حذف و دوبرابر شدن فضا

`MediaStoreExporter` یک **کپی** در `Movies/SalviaBrowxer` یا `Music/SalviaBrowxer` می‌سازد و کپی داخل
برنامه «منبع حقیقت» می‌ماند (کامنت خودش همین را می‌گوید). اما:

- `DownloadsViewModel.deleteDownload` فقط فایل داخلی و `.part` را پاک می‌کند
  (`DownloadsViewModel.kt:78-82`); کپی منتشرشده در MediaStore می‌ماند. `exportedUri` در ردیف ذخیره
  می‌شود و تنها یک مصرف دارد: نمایش/پنهان‌کردن دکمهٔ «Save to gallery» (`DownloadItem.kt:126`) —
  هیچ‌جا برای پاک‌کردن یا بررسی وجود کپی منتشرشده به کار نمی‌رود.
- نتیجه برای کاربر: هر دانلود تمام‌شده دو بار فضا می‌گیرد، و «حذف» دانلود فایل گالری را باقی می‌گذارد.
  roadmap هم «ownership tracking for delete (never delete other apps' media)» را باز گذاشته؛ این
  یافته نشان می‌دهد که نیمهٔ اول کار (استفاده از `exportedUri` با بررسی مالکیت) هنوز پیاده نشده.

### P1-3 — سند وضعیت کهنه، در تناقض با کد

`docs/phase-1-status.md` و `docs/agent-handoff.md` می‌گویند:

- «DASH remains recognized and refused» — در حالی که `DashDownloader`, `DashManifestParser`,
  `TrackMerger` وجود دارند و `BrowserViewModel.unsupportedReasonFor(... dashIsSupported = true)` صریحاً
  DASH را باز می‌کند.
- «Remaining release blockers: Gallery export … private cookie isolation … HLS remux» — در حالی که
  `MediaStoreExporter` و `MediaRemuxer` هر دو در سرویس دانلود صدا زده می‌شوند.

درعوض، README این‌ها را «Shipped» می‌داند. یعنی مخزن دو روایت متناقض از وضعیت خودش دارد. خطر واقعی:
همین حالا `Constants.kt:24` می‌گوید «MPEG-DASH … the app cannot segment it yet, so it is never
offered» — کامنتی که با کدی که در همان مخزن هست نمی‌خواند. هر مشارکت‌کنندهٔ بعدی (یا هر ایجنت) ممکن
است بر اساس سند کهنه چیزی را «دوباره پیاده‌سازی» یا «عمداً رد» کند.

**اقدام:** `phase-1-status.md` و `agent-handoff.md` را یا به‌روزرسانی کن یا با یک هدر `SUPERSEDED —
see README + world-class-roadmap.md` بی‌اثر کن، و `Constants.kt` را با وضعیت واقعی هم‌راستا کن.

### P1-4 — بدهی کوچک اما مشخص در کد مرده و ثابت‌های کهنه

- `Constants.DATABASE_VERSION = 2` در حالی که `AppDatabase` روی `version = 4` است و سه مهاجرت
  (`MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`) دارد — یک منبع حقیقت جعلی برای نسخهٔ پایگاه‌داده.
  `DATABASE_NAME` هم بی‌استفاده است چون `AppModule.kt:88` نام را hardcode کرده.
- `service/MediaDetectionService.kt` (۱۴ خط) **صفر مصرف‌کنندهٔ بیرونی** دارد؛ `BrowserViewModel` مستقیم
  `MediaDetector` را تزریق می‌کند. با قاعدهٔ خود پروژه («ماژول/لایه فقط با مصرف‌کننده»)، این فایل باید حذف
  شود.
- توابع بی‌استفاده: `ValidationUtils.sanitizeUrl`، `FileUtils.{ensureParentDirectory, getFileExtension,
  safeDelete}`، `Extensions.{containsAny, startsWithAny, endsWithAny, formatDate, shareText, toUri,
  toast}`.
- `gradle.properties` هنوز `android.enableJetifier=true` دارد، بدون هیچ کتابخانهٔ legacy؛ فقط زمان
  ساخت را زیاد می‌کند. kapt هم برای Room/Hilt قابل تبدیل به KSP است (سرعت، و سازگاری بهتر با Kotlin 2.x).

### P1-5 — بهداشت مخزن

- `app/build/outputs/apk/release/app-release-unsigned.apk` (۱۹٫۲۷ مگابایت) **در گیت کامیت شده**، در حالی
  که `.gitignore` خودِ مخزن `*.apk` و `/build` را نادیده می‌گیرد. هر clone و هر CI این ۱۹ مگابایت را
  می‌کشد و فایل هم به‌سرعت کهنه می‌شود (نسخهٔ کامیت‌شده با کد امروز یکی نیست).
- `tmp/build_summary.md` و `tmp/release_apk_report.md` هم ردیابی می‌شوند — گزارش‌های یک‌بارمصرف در تاریخ
  پروژه.
- تاریخ گیت یک کامیت squash‌شده است. برای بازبینی «این تغییر چه چیزی را شکست» هیچ ابزاری نمی‌ماند.

### P2 — موارد واقعی ولی کم‌فوریت

- **سازگاری شفافیت ارتباط:** `usesCleartextTraffic="true"` + `MIXED_CONTENT_COMPATIBILITY_MODE`
  (`WebViewSetup.kt:44`) یعنی گیت `CleartextPolicy` فقط **ناوبری تایپ‌شده** را می‌گیرد؛ یک صفحهٔ
  `https` می‌تواند بی‌سروصدا زیرمنبع `http` بار کند. این در مانیفست مستند شده، اما ادعای «http هرگز
  بی‌صدا بار نمی‌شود» دقیق نیست.
- **حالت خصوصی، نیمهٔ دوم ماجرا:** سرکوب تاریخ درست پیاده شده (`BrowserViewModel.addHistoryEntry`)،
  اما کوکی/`localStorage`/`domStorage` مشترک‌اند. برچسب «New private tab» با آیکون قفل ممکن است
  بیش از واقعیت وعده بدهد؛ یک متن توضیح کوتاه در TabSwitcher کم‌هزینه و صادقانه است.
- **بازیابی نشست پس از مرگ پروسه:** `savedStateHandle` فقط برای لینک پیست‌شده استفاده می‌شود؛ تب‌ها
  در `ViewModel` هستند و با مرگ پروسه از بین می‌روند (مرورگر با صفحهٔ پیش‌فرض باز می‌شود). مرورگرهای
  بالغ نشست را بازمی‌گردانند.
- **سیاست کیفیت در تست‌ها:** هیچ تستی برای `DownloadService` وجود ندارد — دقیقاً همان‌جایی که باگ‌های
  P0-3 نشسته‌اند (retry، wifi hold، finalize، throttle). `MediaRemuxer`/`TrackMerger` توجیه
  دارند که فقط روی دستگاه تست شوند، اما منطق سرویس با `MockWebServer` + `TestCoroutineScheduler`
  قابل تست است.
- **CI:** `emulator_verification.yml` فقط روی `pull_request` و `workflow_dispatch` اجرا می‌شود، نه روی
  push به `main`؛ پس ایده‌آل است که مسیر تأییدشده روی دستگاه بعد از merge هم محافظت نشود.
  `android_ci.yml` و `apk_build.yml` هم هر دو روی هر push کل ساخت+آزادسازی را تکرار می‌کنند (هزینه).
- **حریم خصوصی/سیاست فروشگاه:** `docs/privacy-policy.md` می‌گوید فایل‌ها در `Android/data/...`
  می‌مانند؛ در واقعیت روی API 29+ یک **کپی** در `Movies/`/`Music/` منتشر می‌شود. برای فرم Data Safety
  گوگل همین جمله باید دقیق شود («کپی در پوشهٔ عمومی Movies/Music منتشر می‌شود»).

---

## ۵) برنامهٔ پیشنهادی (به ترتیب ارزش)

| اولویت | کار | چرا الان |
| --- | --- | --- |
| ۱ | مالکیت انبار WebView را به ViewModel بده (+`configChanges` برای چرخش) (P0-1) | هر رفت‌وبرگشت به Downloads/Settings صفحه را ریلود می‌کند |
| ۲ | `CookieJar` پل‌شده به `CookieManager` + ذخیرهٔ `referer` در صف (P0-2) | بدون آن، وعدهٔ اصلی روی سایت‌های کوکی‌دار می‌شکند |
| ۳ | رفع مسابقهٔ `stopWhenIdle`، throttle per-download و نشانگر Wi‑Fi غیرمحلی (P0-3) | دانلود گم‌شده/گیرکرده؛ تست سرویس را هم اضافه کن |
| ۴ | `onRenderProcessGone` + `onReceivedError` + `onReceivedSslError` + `setDownloadListener` (P0-4) | جلوگیری از سقوط و صفحهٔ مرده |
| ۵ | سخت‌کردن پل blob (سقف/TTL/گیت اقدام کاربر) و `hasGesture`/`isForMainFrame` (P1-1) | سطح حملهٔ iframe متخاصم |
| ۶ | استفاده از `exportedUri` برای پاک‌سازی کپی گالری با بررسی مالکیت (P1-2) | حذف دانلود باید گالری را هم پاک کند |
| ۷ | یکسان‌سازی مستندات + حذف ثابت‌های کهنه و کد مرده (P1-3/P1-4) | جلوگیری از پیاده‌سازی دوبارهٔ چیزهای موجود |
| ۸ | خارج‌کردن APK و `tmp/` از گیت، اضافه‌کردن مسیر `main` به CI دستگاه (P1-5, P2) | بهداشت مخزن و پوشش پایدار |

---

## ۶) محدودیت‌های همین تحلیل

- **هیچ ساخت/اجرایی انجام نشد.** JDK 17 و Android SDK 36 در این محیط موجود نیست و شبکه به
  `dl.google.com`/Maven باز نیست، پس `:app:testDebugUnitTest`، `assembleDebug` و تست‌های دستگاه
  اجرا نشدند. هر ادعای «صحت» در این سند از خواندن کد می‌آید.
- **تاریخ گیت تک‌کامیتی است**، پس نمی‌توان تشخیص داد کدام رفتار تازه‌نفسکشیده (regression) است و کدام
  از ابتدا این‌طور طراحی شده.
- **رفتار واقعی WebView/شبکه تأیید نشده:** P0-1 (ریلود شدن صفحه در بازگشت از صفحه‌های کتابخانه)،
  P0-2 (شکست کوکی/Referer) و P0-4 (سقوط رندرر) هر سه باید روی یک دستگاه واقعی و با یک سایت واقعی
  بازتولید شوند تا از «فرضیهٔ قوی» به «باگ اثبات‌شده» تبدیل شوند. شواهد کد در هر سه مورد قطعی و
  مستقیم است (تنها P0-2 برای اثبات به سایتی نیاز دارد که رسانه را با کوکی/Referer محافظت کند)؛ شدت
  عملی به سایت‌های هدف بستگی دارد.
- مرزهای اعلام‌شدهٔ پروژه محترم شمرده شده‌اند: هیچ پیشنهادی برای extractor سایت‌محور، تغییر رفتار
  صفحات، عبور از DRM، هاروست توکن یا دانلود live مطرح نشده است. `docs/world-class-roadmap.md`
  همچنان مرجع اولویت است.
