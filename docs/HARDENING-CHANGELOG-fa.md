# گزارش تغییرات سخت‌سازی — SalviaBrowxer

این بسته بر مبنای سورس کامل شاخهٔ `main` ساخته شده است. نسخهٔ اپ عمداً bump نشده؛ ماتریس واقعی دستگاه و تایید انتشار هنوز گیت محصول هستند.

## تغییرات

1. **پایداری CI:** چهار workflow به `actions/checkout@v7`، `actions/setup-java@v6` و در مسیرهای artifact به `actions/upload-artifact@v7` ارتقا یافتند. Emulator runner به `v2.38.0` ارتقا یافت.
2. **کش Gradle:** `gradle/actions/setup-gradle@v6` با `gradle-version: wrapper` و `cache-provider: basic` افزوده شد. CI نسخهٔ Gradle را از Wrapper پروژه می‌گیرد و command `gradle` را از نسخهٔ provision شده اجرا می‌کند.
3. **Timeout محلی Wrapper:** `networkTimeout` از ۶۰ به ۱۸۰ ثانیه افزایش یافت.
4. **PasswordVault:** verifier ذخیره‌شده پیش از اجرای PBKDF2 محدود و اعتبارسنجی می‌شود؛ تکرار بالاتر از ۶۰۰٬۰۰۰، verifier بزرگ‌تر از ۲۵۶ نویسه، salt غیر ۱۶ بایتی و derived key غیر ۳۲ بایتی رد می‌شوند. verifierهای معتبر قدیمی با iterationهای پایین‌تر همچنان پذیرفته می‌شوند؛ حداقل رمز ۶ نویسه تغییر نکرده است.
5. **مستندسازی:** قرارداد حفظ موتور مدیا و گیت‌های مقایسهٔ منصفانه با InShot اضافه شد؛ CONTRIBUTING به گیت‌های تست کامل‌تر ارجاع می‌دهد.

## فایل‌های تغییریافته

- `.github/workflows/android_ci.yml`
- `.github/workflows/apk_build.yml`
- `.github/workflows/emulator_verification.yml`
- `.github/workflows/release.yml`
- `gradle/wrapper/gradle-wrapper.properties`
- `app/src/main/java/com/salvia/salviabrowxer/data/security/PasswordVault.kt`
- `app/src/test/java/com/salvia/salviabrowxer/data/security/PasswordVaultTest.kt`
- `CONTRIBUTING.md`
- `docs/MEDIA-ENGINE-PRESERVATION-fa.md`
- `docs/HARDENING-CHANGELOG-fa.md`
- `docs/analysis-v2-fa.md` (شفاف‌سازی وضعیت تاریخی/جاری KSP، onTimeout و CI)
- `docs/world-class-roadmap.md` (حذف KSP از فهرست کارهای باز)
- `docs/phase-1-status.md` (به‌روزرسانی شمار تست MediaSniffer به ۳۰)

هیچ فایل اجرایی در `core/model`، `media/detector`، `media/resolver`، `media/downloader`، اسکریپت تزریق MediaSniffer یا پل MSE تغییر نکرده است. hash فایل‌های شاخص موتور در `MEDIA-ENGINE-PRESERVATION-fa.md` ثبت شده‌اند.

## اعتبارسنجی انجام‌شده

- `node --test scripts/test-media-sniffer.cjs`: ۳۰/۳۰ موفق.
- تست مستقل Kotlin/JVM برای PasswordVault با shim پلتفرمی: موفق.
- Parse هر چهار YAML workflow: موفق.
- `git diff --check`: بدون whitespace error در تغییرات محلی.
- Build کامل Gradle/Android: در این محیط اجرا نشد؛ `services.gradle.org` به‌دلیل خطای DNS resolve نشد. GitHub Actions باید بعد از Push، تست‌های JVM، lint، APK/AAB و emulator را تأیید کند.
