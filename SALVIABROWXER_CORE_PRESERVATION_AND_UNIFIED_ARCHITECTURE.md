# SalviaBrowxer — قرارداد حفظ هسته و معماری یکپارچه

**نوع سند:** Engineering Baseline / Architecture Contract  
**نسخه:** 1.0  
**تاریخ ثبت:** 2026-11-22  
**مخزن مادر:** SaliSalvia/SalviaBrowxer  
**شاخه مبنا:** main  
**Commit مبنا:** e004cacd749b579c26992cf37397afcaec3a07b5  
**وضعیت:** مبنای مهندسی ثبت‌شده؛ هر تغییر اجرایی باید به این قرارداد ارجاع دهد.

---

## 1. هدف و اصل راهبردی

SalviaBrowxer تنها مخزن مادر، محصول اصلی و مرجع نهایی است. چهار مخزن دیگر فقط منابع انتخابی برای ارتقای آن هستند؛ هیچ‌کدام جایگزین هسته نمی‌شوند. ادغام به معنی انتقال انتخابی قابلیت‌ها و الگوهای اثبات‌شده است، نه کپی کورکورانه یا بازنویسی کامل برنامه.

**اصل غیرقابل مذاکره:** هیچ قابلیت موجود، رفتار کاربر، داده ذخیره‌شده، جزء UI یا مسیر پیمایش بدون ثبت، آزمون رگرسیون هم‌ارز و مسیر بازگشت حذف یا تضعیف نمی‌شود. قابلیت جدید باید در کنار WebView و اجزای فعلی ساخته و سپس با Adapter به مسیر فعلی متصل شود.

### 1.1 اصول الزام‌آور

1. Core Preservation: تمام قابلیت‌های فعلی تا عبور از آزمون رگرسیون حفاظت‌شده‌اند.
2. Additive Architecture: توسعه در کنار اجزای موجود؛ جایگزینی یک‌باره ممنوع.
3. Backward Compatibility: داده‌های Room، DataStore، شناسه برنامه، URL handling و دانلودهای فعلی حفظ شوند.
4. UI Preservation: Cosmic UI، تب‌ها، نوارها، حباب دانلود، پنل کیفیت، Library، DevTools و فارسی/RTL حفظ شوند.
5. Single Source of Truth: برای هر داده فقط یک مرجع پایدار؛ صف دانلود جدید نباید کنار Room یک حقیقت متناقض دوم بسازد.
6. No Blind Porting: انتقال کد فقط پس از بررسی وابستگی، مجوز، بلوغ، امنیت، سازگاری و تست.
7. Evidence-Based Claims: README به‌تنهایی شاهد قابلیت نیست؛ ادعا باید به کد و تست متصل باشد.
8. Regression First: قبل و بعد از هر تغییر، رفتار موجود آزمون شود.
9. Build Stability: Gradle، Manifest، SDK و dependencyها مرحله‌ای تغییر کنند.
10. Measured Performance: ادعای برتری بر InShot یا مرورگرهای دیگر نیازمند Benchmark هم‌شرایط است.
11. Privacy by Design: Incognito، مجوزهای وب، کوکی، Storage و DevTools مرزبندی روشن داشته باشند.
12. Honest Capability Boundaries: DRM، stream زنده، Blob/MSE و APIهای اختصاصی با پشتیبانی قطعی اشتباه نشوند.

---

## 2. Snapshot فنی مخزن مادر

| مورد | وضعیت در Commit مبنا |
|---|---|
| مخزن | https://github.com/SaliSalvia/SalviaBrowxer |
| Branch | main |
| Commit | e004cacd749b579c26992cf37397afcaec3a07b5 |
| Gradle modules | فقط :app |
| Language / UI | Kotlin، Jetpack Compose، Material 3 |
| Browser | Android System WebView |
| namespace | com.example |
| applicationId | com.aistudio.navabrowser.qxmkzt |
| minSdk / targetSdk | 24 / 36 |
| compileSdk | 36.1 |
| CI toolchain | JDK 21، Gradle 9.3.1، Android SDK 36.1 |
| Room | نسخه 2، DB با نام salvia_browser_db |
| App version | versionCode 1، versionName 1.0 |
| Default branch protection | در Snapshot بررسی‌شده فعال نبود |

### 2.1 CI مبنا

دو اجرای آخر روی Commit مبنا موفق بودند:
- Run 44961629725، ناشی از Push: موفق.
- Run 44961948396، اجرای دستی: موفق.

در Run 44961948396، مراحل JDK، Android SDK، Gradle، Unit Tests، Debug APK، Release APK امضاشده، اعتبارسنجی Artifact و Upload موفق شدند. مرحله انتشار GitHub Release به دلیل اجرا نشدن روی Tag، Skipped شد.

این شواهد موفقیت CI و تولید APK را نشان می‌دهد؛ نه تست کامل روی دستگاه واقعی، نه اثبات پایداری دانلود پس از مرگ Process و نه اثبات جداسازی حریم خصوصی.

### 2.2 هویت انتشار و داده‌ها

تا زمان تصمیم مستقل درباره انتشار عمومی، موارد زیر تغییر نکنند:
- applicationId و signing identity
- namespace و packageهای فعلی
- نام DB، schema و قرارداد Entityها
- محل و URI فایل‌های تکمیل‌شده
- قراردادهای Room و DataStore

تغییر applicationId می‌تواند امکان Update نصب‌های قبلی را از بین ببرد. تغییر شناسه، کلید امضا، DB name یا storage layout باید طرح مهاجرت جداگانه داشته باشد.

---

## 3. موجودی قابلیت‌های حفاظت‌شده هسته

وضعیت‌ها:
- **موجود:** پیاده‌سازی در درخت فعلی مشاهده شده است.
- **محدود:** پیاده‌سازی بخشی از دامنه را پوشش می‌دهد یا تست کافی ندارد.
- **شکاف:** قابلیت هنوز در هسته فعلی وجود ندارد.
- **ادعای تأییدنشده:** README یا طراحی ذکر می‌کند، اما کد فعلی اثبات نمی‌کند.

| ID | قابلیت | مالک فعلی | وضعیت و الزام حفاظت |
|---|---|---|---|
| CORE-APP-01 | راه‌اندازی و Activity | app/src/main/java/com/example/MainActivity.kt | موجود؛ نقطه ورود و چرخه عمر حفظ شود |
| CORE-BRW-01 | WebView و تنظیمات Chromium | ui/components/WebViewContainer.kt، engine/WebViewConfigurator.kt | موجود؛ UA، JavaScript، DOM storage، Safe Browsing، Zoom، فایل و Popup حفظ شوند |
| CORE-TAB-01 | مدیریت تب‌ها | ui/BrowserViewModel.kt، ui/BrowserModels.kt، TabSwitcherScreen.kt | موجود؛ شناسه‌ها، انتخاب تب، MRU، وضعیت هر تب و رفتار بازگشت حفظ شوند |
| CORE-MEM-01 | Hibernation و حافظه تب | BrowserViewModel.kt | موجود؛ سقف 2 WebView زنده در دستگاه کم‌رم و 3 در دستگاه معمولی، Pause، saveState و restore حفظ شوند |
| CORE-NAV-01 | Navigation و نوار آدرس | BrowserViewModel.kt، BrowserTopBar.kt، BrowserBottomBar.kt | موجود؛ Back، Forward، Reload، Stop، Home و جست‌وجوی ورودی حفظ شوند |
| CORE-NAV-02 | Mobile/Desktop User-Agent | WebViewConfigurator.kt | موجود؛ حذف wv token برای سازگاری OAuth حفظ شود |
| CORE-DATA-01 | Bookmark و Speed Dial | data/local/Entities.kt، Daos.kt، BrowserRepository.kt، LibrarySheets.kt، StartPageScreen.kt | موجود؛ داده، دسته‌بندی و Seed اولیه حفظ شوند |
| CORE-DATA-02 | History | Room، BrowserRepository.kt، BrowserViewModel.kt | موجود؛ Incognito نباید وارد History عادی شود؛ سقف اخیر 300 رکورد حفظ شود |
| CORE-DATA-03 | Download records | DownloadEntity، DownloadDao، BrowserRepository.kt | موجود؛ رکورد، وضعیت، URI، حجم و زمان حفظ شوند |
| CORE-PREF-01 | Settings | PreferencesRepository.kt | موجود؛ AdBlock، Tracker، HTTPS، JavaScript، DNT، cookies، search engine، theme و language حفظ شوند |
| CORE-I18N-01 | فارسی/انگلیسی و RTL | ui/i18n/AppStrings.kt، res/values-fa، res/values | موجود؛ رشته‌ها، فونت Vazirmatn و RTL حفظ شوند |
| CORE-UI-01 | Cosmic UI و هویت تصویری | ui/theme، ui/components/CosmicChrome.kt، res/drawable | موجود؛ لوگو، پس‌زمینه و زبان بصری حذف یا تضعیف نشوند |
| CORE-AD-01 | Ad/Tracker blocking | engine/AdBlockerEngine.kt | موجود؛ قواعد سفارشی، whitelist، redirect/popup و cosmetic filtering حفظ شوند |
| CORE-EXT-01 | Extensions/UserScripts | engine/ExtensionEngine.kt، ExtensionsSheet.kt، ExtensionEntity | موجود اما WebExtensions کامل نیست؛ JS/CSS سفارشی و document_start/end حفظ شود |
| CORE-DEV-01 | DevTools | DevToolsSheet.kt، BrowserViewModel.kt | موجود؛ Console، Network، DOM، Meta، Cookies و Storage حفظ شوند؛ اطلاعات خصوصی باید فیلتر شوند |
| CORE-MEDIA-01 | Media detection | engine/MediaSnifferEngine.kt، WebViewContainer.kt، BrowserViewModel.kt | موجود اما محدود؛ fetch/XHR، DOM، JSON-LD، OpenGraph و ranking فعلی حفظ شود |
| CORE-MEDIA-02 | Media quality model | BrowserModels.kt، MediaSnifferEngine.kt، MediaDownloaderSheet.kt | موجود؛ URL، quality، MIME، audioUrl، HLS/DASH و قرارداد UI حفظ شود |
| CORE-DL-01 | Floating Download Bubble | FloatingDownloadBubble.kt | موجود؛ جایگاه و تعامل فعلی حفظ شود |
| CORE-DL-02 | Direct downloader | engine/MediaDownloadManager.kt | موجود اما lifecycle-bound؛ دانلود مستقیم، Range تا 4 بخش، Pause/Resume/Cancel و سرعت حفظ شود |
| CORE-DL-03 | HLS downloader | MediaSnifferEngine.kt، MediaDownloadManager.kt | محدود؛ Master variants، audio rendition، segmentهای ترتیبی، AES-128 پایه، EXT-X-MAP و mux attempt حفظ شوند |
| CORE-DL-04 | DASH downloader | MediaSnifferEngine.kt، MediaDownloadManager.kt | محدود؛ استخراج BaseURL و تلاش برای video/audio حفظ شود؛ SegmentTemplate/SIDX کامل فرض نشود |
| CORE-DATA-04 | Room schema / migrations | BrowserDatabase.kt | پرریسک؛ migration مخرب مجاز نیست و fallbackToDestructiveMigration باید پیش از Release حذف شود |
| CORE-SEC-01 | SSL / external navigation | WebViewContainer.kt | موجود؛ warning و safe back حفظ شود |
| CORE-SEC-02 | Web permission handling | WebViewContainer.kt | ریسک امنیتی؛ grant خودکار request.resources باید با رضایت و مجوز Android جایگزین شود، نه حذف قابلیت مجاز |
| CORE-PRIV-01 | Incognito | BrowserModels.kt، BrowserViewModel.kt، WebViewConfigurator.kt | ناقص؛ پاک‌سازی WebStorage سراسری ممکن است داده Normal را حذف کند؛ UI و عدم ثبت History حفظ شوند |
| CORE-TEST-01 | تست‌های JVM و instrumentation | app/src/test، app/src/androidTest | موجود اما محدود؛ آزمون‌های جدید باید رفتارهای حیاتی را پوشش دهند |
| CORE-CI-01 | Build/Test workflow | .github/workflows/build-apk.yml | موجود؛ Debug، tests، Release و artifact flow حفظ شوند |

**قاعده:** این فهرست یک inventory اولیه است؛ پیش از refactor هر ردیف باید با مسیر دقیق کد، تست مشخص و رفتار قابل بازتولید تکمیل شود. «حفظ هسته» به معنی ثابت ماندن باگ امنیتی نیست؛ باگ باید اصلاح شود اما دسترسی و قابلیت محصول حفظ شود.

---

## 4. ممیزی شکاف‌های قطعی در Snapshot

1. **Blob/MSE:** توضیح کلاس MediaSnifferEngine به MSE اشاره می‌کند، اما isCandidateMediaUrl و parseDomMediaPayload آدرس blob: و data: را رد می‌کنند. اسکریپت fetch/XHR و DOM به‌تنهایی capture واقعی MSE نیست.
2. **Incognito:** clearIncognitoAndVolatileData از WebStorage.deleteAllData استفاده می‌کند؛ حذف Storage می‌تواند روی تمام WebViewهای همان profile اثر کند. نباید آن را جداسازی واقعی دانست.
3. **Web permissions:** onPermissionRequest در WebViewContainer تمام resourceهای درخواستی را grant می‌کند؛ gate مبدأ، Runtime permission و رضایت UI لازم است.
4. **Download lifecycle:** MediaDownloadManager به CoroutineScope داده‌شده از BrowserViewModel متصل است و BrowserViewModel آن را با viewModelScope ایجاد می‌کند. این سرویس برای توقف/مرگ Process پایداری مستقل ندارد.
5. **Range validation:** مسیر چندبخشی باید 206، Content-Range، تطابق طول و محدوده درخواستی را دقیق اعتبارسنجی کند؛ قبول صرف پاسخ successful یا 200 کافی نیست.
6. **HLS:** مسیر فعلی HLS، playlist را به‌صورت ترتیبی می‌خواند؛ EXT-X-BYTERANGE به‌طور کامل مدیریت نمی‌شود و خطای decrypt نباید به نوشتن ciphertext به‌عنوان خروجی سالم منتهی شود.
7. **DASH:** parser فعلی به BaseURL متکی است؛ پشتیبانی SegmentTemplate، SegmentTimeline، SegmentList و SIDX اثبات نشده است.
8. **Resume:** direct HTTP دارای فایل‌های موقت و Range است؛ HLS/DASH مسیر resume پایدار و recovery معادل direct ندارد.
9. **Room:** BrowserDatabase نسخه 2 از fallbackToDestructiveMigration(true) استفاده می‌کند؛ این رفتار برای داده واقعی پذیرفته نیست.
10. **Claims:** CI موفق، معادل تست واقعی دستگاه یا اثبات برتری از InShot نیست.

---

## 5. نقش چهار مخزن مرجع و تصمیم انتقال

### 5.1 Salvia--Browxer — مرجع کمکی Kotlin

منابع کاندید:
- BrowserHost / BrowserHostDelegate و SitePermissionStore
- BrowserDetectorScript و MediaSniffer
- RequestHeaderStore، MediaProber، RangeChunkPlanner
- NativeDownloadService و DownloadQueueStore
- HlsKeyParser، DashManifest، SidxParser، NativeMediaMuxer
- MediaPublisher و MediaPlayerScreen مبتنی بر Media3
- CrashReporter

**محدودیت حیاتی:** commit فعلی 987e442237a6d5f3d580b70a69771877496925af و اسناد Reality Check مورخ 2026-11-21 نشان می‌دهند اسناد قدیمی parity بعضی کلاس‌ها و تست‌ها را به‌اشتباه shipped معرفی کرده بودند. بعضی اجزای MSE capture، SAF policy، transcoding و instrumentation در درخت فعلی وجود ندارند. پس فقط فایل موجود و تست‌پذیر قابل انتقال است؛ ادعاهای اسناد قدیمی منبع حقیقت نیستند.

تصمیم: ابتدا NativeDownloadService/queue، RangeChunkPlanner، HLS parser و SAF publisher به‌صورت مستقل ممیزی شوند؛ انتقال تنها پس از تست fixture و سازگاری با Room انجام شود. DownloadQueueStore JSON نباید source of truth دوم شود.

### 5.2 Salvia-BRWSR — مرجع معماری چندماژوله

منابع کاندید:
- تفکیک core/common، data، database، domain، UI
- engine/sniffer، ranker، resolver، downloader و muxer
- feature/browser، download_sheet، library، player و settings
- macrobenchmark و startup benchmark

محدودیت: ساختار چندماژوله و برخی بخش‌ها scaffold یا ناقص هستند؛ build و TODOها باید مجزا بررسی شوند.

تصمیم: از مرزبندی مسئولیت‌ها و dependency direction الهام گرفته شود؛ انتقال کامل Gradle modules در ابتدای کار ممنوع است. ابتدا package boundary داخل :app مرتب شود؛ extraction واقعی پس از تثبیت قراردادها.

### 5.3 SalviaBrowXr — منبع الگوریتم‌های TypeScript

منابع کاندید:
- mainContentSelector.ts: طبقه‌بندی MAIN/SUGGESTED/AD و امتیازدهی Candidate
- linkPatternEngine.ts: canonical URL، حذف tracking و تشخیص direct/platform page
- batteryPolicy.ts: محدودسازی منابع بر اساس battery saver
- retryPolicy.ts: retry status، exponential backoff و jitter
- memoryCachePolicy.ts: محدود کردن cache و تعداد candidateها
- siteShield.ts: خلاصه امنیت و تنظیمات سایت
- streamRecorder.ts: مسیر capture مشروط و capability detection؛ قابل تعمیم به همه WebViewها نیست

تصمیم: منطق‌های pure و مستقل به Kotlin پورت و با Unit Test اثبات شوند. TypeScript/React runtime به هسته Compose اضافه نشود. هر منطق وابسته به browser API باید در Android/WebView با محدودیت‌هایش بازطراحی شود.

### 5.4 Salvia---Browxer — منبع مکمل Bridge و Link Pattern

منابع کاندید:
- SalviaNativeBridge و BrowserBridgeInjector
- LinkPatternDetector
- RangeChunkPlanner و parserهای مشترک
- NativeDownloadService و MediaPublisher

محدودیت: هم‌پوشانی زیاد با SalviaBrowXr و Salvia--Browxer؛ آخرین commit f9134c7 اصلاح build issue در Kotlin bridge را گزارش می‌کند اما سطح بلوغ کلی باید جداگانه اثبات شود.

تصمیم: فقط قطعاتی که در منابع دیگر بهتر نیستند و تست/قرارداد روشنی دارند وارد شوند.

---

## 6. معماری مقصد افزایشی

در فاز اول همچنان یک Gradle module یعنی :app باقی می‌ماند. مرزهای package و interface اضافه می‌شوند و تنها بعد از تثبیت قراردادها، در فاز متأخر ماژول‌های Gradle استخراج می‌شوند.

~~~text
Compose UI / Existing Cosmic UX
  ├─ Browser bars, tabs, sheets, DevTools
  ├─ Existing Download Bubble and Quality Sheet
  └─ Existing Library / History / Bookmarks
           │
           ▼
Presentation Adapters
  ├─ BrowserViewModel (existing UI state owner)
  ├─ Media UI Adapter
  └─ Download UI Adapter
           │
           ▼
Application Contracts
  ├─ BrowserSession / Tab Registry
  ├─ MediaDetectionCoordinator
  ├─ DownloadCoordinator
  └─ Profile / SitePermission contracts
           │
           ▼
Domain Engines
  ├─ Media candidate admission + ranking
  ├─ URL/link pattern classifier
  ├─ HTTP/HLS/DASH resolvers
  ├─ Download scheduler + retry policy
  └─ Privacy / permission policy
           │
           ▼
Platform & Data Adapters
  ├─ Existing WebView
  ├─ Room (single durable data source)
  ├─ DataStore
  ├─ Android Foreground Service / WorkManager as justified
  ├─ MediaStore / SAF
  └─ Media3 Player
~~~

### 6.1 Browser Core

WebView فعلی و BrowserViewModel در ابتدا حفظ می‌شوند. امکانات جدید از طریق interface و adapter به آن متصل شوند. یک WebView engine موازی یا React browser نباید جایگزین WebView فعلی شود.

### 6.2 Media Intelligence

لایه تشخیص آینده باید Candidate را همراه evidence ثبت کند:
- source page URL و tab ID
- media URL، MIME، response status و length در صورت در دسترس بودن
- DOM geometry، visibility، duration و source element
- playlist/representation و کیفیت‌های شناسایی‌شده
- زمان مشاهده و منبع شناسایی (network، DOM، metadata)
- ad/suggested hints و confidence score

Evidence source نباید با حدس از URL یا نام فایل جایگزین شود. نامزد باید به tab و صفحه‌ای که از آن دیده شده منتسب بماند. Candidate دیررس از صفحه قدیمی نباید وارد tab جدید شود.

### 6.3 Download Engine

قراردادهای پیشنهادی:
- DownloadTask: شناسه پایدار، tab/profile context، source URL، نوع media، state، progress، staging files، destination و timestamps.
- DownloadRepository: تنها مرجع durable؛ ابتدا Room.
- DownloadScheduler: محدودیت هم‌زمانی و fairness.
- DownloadExecutor: direct HTTP، HLS و DASH به‌صورت strategy جدا.
- RetryPolicy: status و خطاهای قابل retry با backoff و jitter؛ خطاهای DRM/unsupported/cancel نباید retry بی‌نهایت شوند.
- DownloadService: اجرا مستقل از ViewModel، notification و cancellation صحیح؛ نوع Foreground Service باید با Android target و نسخه‌های واقعی تطبیق داده شود.
- Existing Download UI: فقط از Adapter به repository/state جدید متصل شود؛ شناسه‌ها و سوابق فعلی حفظ شوند.

### 6.4 Privacy & Profiles

- BrowserProfile abstraction پیش از انتخاب روش جداسازی تعریف شود.
- هر Tab باید مشخص کند متعلق به Normal یا Private profile است.
- پشتیبانی واقعی storage/cookie isolation به نسخه System WebView و AndroidX WebKit دستگاه‌های هدف وابسته است و باید آزمایش شود.
- اگر جداسازی واقعی ممکن نیست، fallback باید صریح و fail-closed باشد؛ پاک‌سازی سراسری Storage راه‌حل نیست.
- تب خصوصی نباید در History عادی، پیشنهادها، diagnostics یا metadata حساس باقی بماند.

### 6.5 Web permissions و WebView security

- origin، resource type، Runtime permission و رضایت کاربر بررسی شوند.
- Camera/Microphone/Location فقط با UX روشن و grant محدود صادر شوند.
- origin ناشناخته، resource نامعتبر یا callback دیررس به‌صورت پیش‌فرض رد شود.
- Permission per-site فقط پس از رضایت کاربر ذخیره شود و قابل لغو باشد.
- SSL warning و Back to Safety حفظ شوند.
- JavaScript bridge allowlist داشته باشد و ورودی صفحه validate شود.

---

## 7. برنامه ادغام مرحله‌ای و گیت‌های پذیرش

| فاز | هدف | کارهای اصلی | گیت عبور |
|---|---|---|---|
| 0 | Baseline Lock | ثبت inventory، commit، dependency، test map و وضعیت build | همه قابلیت‌ها owner و رفتار آزمون‌پذیر داشته باشند |
| 1 | Contract & Regression | تست Tab/MRU/Hibernation، Navigation، History، Bookmark، AdBlock، Extension، DevTools، Download UI و RTL | تست‌های موجود سبز؛ رفتارهای فاقد پوشش دارای تست شوند |
| 2 | Privacy & Permissions | Profile abstraction، رفع پاک‌سازی سراسری، permission gate و origin validation | ثابت شود Incognito داده Normal را حذف نمی‌کند؛ allow/deny تست شود |
| 3 | Media Intelligence | Candidate/evidence model، admission، ranking، URL classifier و tab attribution | fixture matrix، false positive/negative و عدم اختلال playback |
| 4 | Download Execution | service/coordinator، Room queue واحد، recovery، notification و cancellation | بستن UI، rotation، stop service و process death تست شوند |
| 5 | HTTP/HLS/DASH | Range validation، resume، HLS BYTERANGE/MAP/AES-128، DASH subset، retry و mux verification | fixture، fake server، interruption و resume واقعی |
| 6 | Player & Library | Media3 player، playlist، SAF/MediaStore و metadata | فایل‌ها با extractor/player باز شوند؛ حذف و انتقال امن |
| 7 | Browser Hardening | Site Shield، permission UX، extension permissions، diagnostics و customization | تست امنیت، UX و RTL |
| 8 | Modularization | استخراج Domain/Engine/Data/Feature به Gradle modules | dependency graph بدون cycle و CI سبز |
| 9 | Benchmark & Release | آزمون هم‌شرایط با رقبا و release hardening | نتیجه اندازه‌گیری‌شده و APK قابل نصب/Update |

### 7.1 Regression Checklist

- [ ] ایجاد/انتخاب/بستن تب و MRU
- [ ] Back / Forward / Reload / Stop / Home
- [ ] Hibernation و restore تب‌ها با سقف حافظه فعلی
- [ ] Popup و target=_blank
- [ ] Mobile/Desktop UA و OAuth
- [ ] History و Bookmark در تب عادی
- [ ] عدم ثبت History در Incognito
- [ ] Incognito نتواند Storage تب عادی را پاک کند
- [ ] AdBlock/Tracker و Google auth whitelist
- [ ] Extensions در document_start/document_end
- [ ] Console/Network/DOM/Storage در DevTools
- [ ] Download Bubble و Quality Sheet
- [ ] Direct download، Pause، Resume، Cancel و وضعیت‌ها
- [ ] HLS/DASH fallback با MIME/پسوند درست
- [ ] فارسی، RTL، انگلیسی و Theme
- [ ] Camera/Microphone/Location allow/deny
- [ ] SSL warning و بازگشت امن
- [ ] Update نصب‌شده با همان applicationId و signing key

### 7.2 Rollback و مهاجرت

- هر فاز branch/commit مستقل و قابل برگشت داشته باشد.
- Adapter یا feature flag مسیر قدیمی را تا اثبات parity حفظ کند.
- Room migration نسخه‌دار و تست‌شده باشد؛ migration مخرب ممنوع.
- Downloadهای فعلی و URIهای تکمیل‌شده نباید ناپدید شوند.
- فایل staging تنها بعد از بررسی مالکیت و وضعیت task حذف شود.
- دو executor نباید برای یک DownloadTask هم‌زمان فعال باشند.
- اگر موتور جدید شکست خورد، fallback کنترل‌شده به مسیر فعلی ممکن باشد.

---

## 8. Benchmark و اثبات رقابت

عبارت «بهتر از InShot» تنها پس از آزمون هم‌شرایط قابل استفاده است.

### 8.1 ماتریس

- حداقل 10 وب‌سایت/نوع پلتفرم عمومی با محتوای مجاز برای آزمون.
- حداقل 3 اجرای مستقل برای هر سناریو روی دستگاه و شبکه یکسان.
- Direct MP4، چندکیفیت، HLS VOD، audio rendition، BYTERANGE، DASH ایستا و SPA/lazy loading.
- قطع شبکه، تغییر اتصال، Pause/Resume، بستن UI، کمبود فضای خالی و توقف Process.
- سناریوهای منفی: pre-roll، preview، تکراری، سند، پاسخ Range ناقص، DRM و Live غیرقابل‌پشتیبانی.

### 8.2 شاخص‌ها

| شاخص | تعریف |
|---|---|
| Detection Precision | درصد candidateهای ارائه‌شده که واقعاً قابل دانلودند |
| Detection Recall | درصد مدیای قابل دانلود آزمون که شناسایی می‌شود |
| Main Video Accuracy | انتخاب ویدیوی اصلی به‌جای ad/preview/related |
| Quality Accuracy | صحت رزولوشن/bitrate/track نسبت به گزینه |
| Download Completion | نرخ Task کامل با خروجی معتبر |
| Resume Integrity | صحت فایل بعد از Pause/Resume و قطع شبکه |
| Output Validity | بازشدن container و صحت audio/video tracks |
| Time to Detection | زمان از شروع مشاهده تا عرضه candidate |
| Throughput | نرخ انتقال با ثبت شبکه و تعداد اتصال |
| Resource Cost | RAM، CPU، battery و فضای موقت |
| Stability | Crash، ANR، WebView reload و اختلال playback |

مقایسه باید با URL، کیفیت، دستگاه، شبکه، فضای آزاد و تعداد اجرای برابر باشد. شکست‌ها و حدود پشتیبانی نیز گزارش شوند. DRM و محتواهای محافظت‌شده نباید به‌عنوان دانلود ناموفق قابل رفع تلقی شوند.

---

## 9. سیاست کیفیت و امنیت

- دورزدن DRM، paywall، توکن خصوصی یا کنترل دسترسی سایت هدف پروژه نیست.
- stream غیرقابل دسترس باید پیام صادقانه بدهد؛ فایل ناقص/مصنوعی نباید کامل معرفی شود.
- Candidate و Download باید به Tab و URL مبدأ منتسب باشند.
- نام فایل و مسیر sanitize شوند؛ دسترسی local file از WebView محدود بماند.
- log و diagnostics نباید cookie، token یا URL دارای secret را بی‌دلیل صادر کنند.
- هر dependency جدید با minSdk 24 و targetSdk 36 سازگار و آزمون‌شده باشد.
- ادعای پشتیبانی همه سایت‌ها، MSE کامل یا برتری قطعی بدون fixture و benchmark ممنوع است.

---

## 10. تصمیم‌های ثبت‌شده و موارد باز

### تصمیم‌های قطعی

1. SalviaBrowxer تنها مخزن مادر است.
2. WebView، Cosmic UI و تمام رفتارهای فعلی حفاظت می‌شوند.
3. قابلیت‌های چهار ریپو انتخابی و افزایشی وارد می‌شوند.
4. Download Engine آینده مستقل از viewModelScope است و Room مرجع وضعیت صف باقی می‌ماند.
5. Incognito نباید Storage عادی را پاک کند.
6. Gradle multi-module extraction به فاز متأخر موکول است.
7. قبل از انتقال هر قابلیت، تست منبع و مقصد بررسی می‌شود.
8. ادعای برتری نیازمند benchmark تکرارپذیر است.

### موارد باز قبل از اجرای فازهای حساس

- نسخه‌های واقعی Android System WebView روی دستگاه‌های هدف چیست؟
- کدام نسخه‌ها profile/storage isolation قابل اتکا دارند؟
- نوع مجاز Foreground/User-Initiated Data Transfer Service برای targetSdk فعلی چیست؟
- Downloadهای Room فعلی چگونه به queue جدید migrate می‌شوند؟
- محدودیت parserهای HLS/DASH با fixture دقیقاً کجاست؟
- چه سایت‌هایی برای آزمون مجاز و تکرارپذیر انتخاب می‌شوند؟
- آیا applicationId فعلی شناسه نهایی انتشار است؟

---

## 11. منابع برای ممیزی

### مخزن مادر
- https://github.com/SaliSalvia/SalviaBrowxer
- https://github.com/SaliSalvia/SalviaBrowxer/commit/e004cacd749b579c26992cf37397afcaec3a07b5
- https://github.com/SaliSalvia/SalviaBrowxer/actions/runs/44961948396
- https://github.com/SaliSalvia/SalviaBrowxer/actions/runs/44961629725

### مخازن مرجع و Commitهای بررسی‌شده
- Salvia--Browxer — 987e442237a6d5f3d580b70a69771877496925af
- Salvia-BRWSR — 88b0dcd1d8d112ea5948b00760b3d4e6d9b99ed2
- SalviaBrowXr — 735ef815ed73481793c5086fc43c220ca9d3deed
- Salvia---Browxer — f9134c7ec95c6ddb61f8ab60772f10a3c12ab918

---

## 12. شرط آغاز و کنترل تغییرات

تا زمان ثبت baseline و تست‌های Phase 0، تغییر گسترده در معماری، جابه‌جایی فایل‌ها، Schema، جایگزینی Download Manager یا بازنویسی WebView/Incognito مجاز نیست.

اولین ادغام اجرایی باید کوچک، قابل برگشت و تست‌پذیر باشد. اولویت شروع: تکمیل قرارداد Media Candidate و ranking بدون حذف مسیر فعلی؛ سپس Privacy/Permission و در ادامه Download Coordinator مستقل. هر commit باید فهرست قابلیت‌های دست‌نخورده، تست اجراشده، وضعیت build و محدودیت‌های باقی‌مانده را گزارش کند.
