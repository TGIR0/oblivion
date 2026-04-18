# Gradle Playbook (FA + EN) — Oblivion

این سند «صفر تا صد» سیستم Gradle این ریپو را توضیح می‌دهد: از ساختار و سیاست ریپازیتوری‌ها تا config cache، dependency verification، امضا (signing)، Firebase و ساخت AARهای Go.

---

## فارسی (FA)

### 1) نقشه فایل‌ها (Repository Map)

- `settings.gradle`
  - تنظیم `pluginManagement` و `dependencyResolutionManagement`
  - `RepositoriesMode.FAIL_ON_PROJECT_REPOS` برای جلوگیری از repoهای پراکنده داخل ماژول‌ها
  - ریپوهای اصلی: `google()`, `mavenCentral()`
  - ریپوی Kotlin EAP فقط برای گروه‌های Kotlin (content-filter شده)
  - `jitpack.io` فقط برای `com.github.erfansn` (hardening)
- `build.gradle` (ریشه)
  - تعریف پلاگین‌ها با Version Catalog (alias)
  - پیکربندی `spotless` (فرمت کد/فایل‌ها)
  - یک‌جا کردن Kotlin compiler args برای subprojectها
  - تسک‌های `versionAudit` و `versionAuditFail` (اجرای `version_audit.ps1`)
- `gradle.properties`
  - پیش‌فرض‌های سرعت/پایداری Gradle (Daemon, Config Cache, Parallel, VFS Watch, …)
- `gradle/libs.versions.toml`
  - Version Catalog: نسخه‌های AGP/Kotlin و وابستگی‌ها/پلاگین‌ها
- `gradle/wrapper/gradle-wrapper.properties`
  - نسخه Gradle Wrapper + `distributionSha256Sum` برای سخت‌سازی
- `gradle/verification-metadata.xml`
  - Dependency Verification (SHA-256)
- `buildSrc/`
  - build-logic سبک برای ValueSourceها (ورودی‌های قابل رهگیری برای Configuration Cache)
- `app/build.gradle`
  - تنظیمات AGP، Compose، دیپندنسی‌ها
  - کنترل Firebase با وجود/عدم وجود `app/google-services.json`
  - Signing release از `keystore.properties` / env / gradle properties + تسک `validateReleaseSigning`
  - ساخت `tun2socks.aar` (Go + gomobile) + تسک `verifyTun2socksAar`

---

### 2) سیاست نسخه‌ها (Bleeding-edge / Always latest)

این پروژه intentionally روی نسخه‌های Alpha/RC حرکت می‌کند (مثلاً Gradle RC، AGP Alpha، Kotlin RC).

ابزار اصلی کنترل نسخه‌ها:
- `./gradlew versionAudit` (صرفاً گزارش)
- `./gradlew versionAuditFail` (در صورت outdated شدن، fail می‌کند)

`version_audit.ps1` علاوه بر کتابخانه‌ها، **Gradle Wrapper RC** را هم با `services.gradle.org` چک می‌کند.

---

### 3) Gradle Daemon + Configuration Cache (پیش‌فرض روشن)

پیش‌فرض‌ها در `gradle.properties`:
- Daemon: روشن
- Configuration Cache: روشن + `org.gradle.configuration-cache.problems=fail`
- Parallel + VFS Watch: روشن

فرمان‌های مهم:
```bash
# خاموش کردن موقت config-cache
./gradlew :app:assembleDebug --no-configuration-cache

# خاموش کردن daemon
./gradlew :app:assembleDebug --no-daemon

# دیدن وضعیت daemonها
./gradlew --status
```

نکته امنیتی:
- Configuration Cache می‌تواند بعضی مقادیر (حتی secrets) را در `.gradle/configuration-cache/` سریالایز کند.
- اگر روی سیستم shared کار می‌کنید، با احتیاط از config-cache استفاده کنید.

---

### 4) Dependency Verification (سخت‌سازی زنجیره تامین)

فایل: `gradle/verification-metadata.xml`

هدف:
- اطمینان از اینکه artifactهای دانلودی همان چیزی هستند که انتظار داریم (SHA-256).

به‌روزرسانی metadata (وقتی dependency جدید اضافه می‌شود یا نسخه‌ها تغییر می‌کند):
```bash
./gradlew --write-verification-metadata sha256 help
./gradlew --write-verification-metadata sha256 :app:assembleDebug -PskipTun2socksBuild=true
```

عیب‌یابی:
- اگر build گفت “Dependency verification failed … update gradle/verification-metadata.xml”، همان دستور بالا را اجرا کنید.
- گزارش جزئیات:
  - `build/reports/dependency-verification/**/dependency-verification-report.html`

---

### 5) Firebase (فعال/غیرفعال خودکار و config-cache-safe)

اگر فایل زیر وجود داشته باشد:
- `app/google-services.json`

در این حالت پلاگین‌های زیر apply می‌شوند و وابستگی‌های Firebase اضافه می‌شوند:
- `com.google.gms.google-services`
- `com.google.firebase.crashlytics`
- `com.google.firebase.firebase-perf`

اگر فایل وجود نداشته باشد، Firebase به‌صورت امن غیرفعال می‌ماند (برای buildهای OSS/بدون کلیدها).

---

### 6) Release Signing (Validation task به‌جای taskGraph hacks)

تنظیمات release signing از یکی از مسیرهای زیر خوانده می‌شود (اولویت بالا به پایین):
1) Gradle property (مثلاً `-POBLIVION_STORE_FILE=...`)
2) Environment variable (مثلاً `OBLIVION_STORE_FILE=...`)
3) فایل `keystore.properties` در ریشه پروژه (gitignored)

کلیدهای `keystore.properties`:
```properties
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

برای جلوگیری از buildهای release بدون امضا، تسک زیر به release tasks وصل شده:
- `:app:validateReleaseSigning`

مثال:
```bash
./gradlew :app:assembleRelease -PskipTun2socksBuild=true
```

اگر signing تنظیم نشده باشد، با پیام راهنما fail می‌کند.

---

### 7) Tun2Socks (Go/Gomobile → AAR)

AAR خروجی:
- `app/libs/tun2socks.aar`

تسک‌ها:
- `:app:buildTun2SocksAar` — ساخت AAR
- `:app:verifyTun2socksAar` — اگر `-PskipTun2socksBuild=true` باشد و AAR وجود نداشته باشد، fail می‌کند

فلگ‌ها:
- `-PskipTun2socksBuild=true`
  - ساخت AAR را skip می‌کند، ولی وجود `app/libs/tun2socks.aar` را enforce می‌کند.
- `-PforceTun2socksRebuild=true`
  - AAR را حتی اگر up-to-date باشد، دوباره build می‌کند.

مثال‌ها:
```bash
# ساخت AAR
./gradlew :app:buildTun2SocksAar

# Build سریع debug بدون ساخت AAR (اگر AAR موجود باشد)
./gradlew :app:assembleDebug -PskipTun2socksBuild=true
```

---

### 8) ترفندها و فرمان‌های حرفه‌ای Gradle

```bash
# لیست تسک‌ها
./gradlew tasks

# توضیح یک تسک
./gradlew help --task :app:assembleDebug

# دیدن dependency tree
./gradlew :app:dependencies

# پیدا کردن اینکه چرا یک dependency آمده
./gradlew :app:dependencyInsight --dependency okhttp --configuration debugRuntimeClasspath

# اطلاعات محیط build (پلاگین‌ها/Gradle)
./gradlew buildEnvironment

# گزارش performance
./gradlew :app:assembleDebug --profile
```

---

## English (EN) — Executive Summary

### What matters
- Gradle Daemon + Configuration Cache are enabled by default in `gradle.properties`.
- Dependency verification is enforced via `gradle/verification-metadata.xml`.
- Firebase is auto-enabled only when `app/google-services.json` exists.
- Release signing is validated via `:app:validateReleaseSigning` (no `taskGraph.whenReady` hacks).
- `tun2socks.aar` is built via Go/gomobile, with `-PskipTun2socksBuild=true` support.

### Common commands
```bash
./gradlew :app:assembleDebug -PskipTun2socksBuild=true
./gradlew :app:buildTun2SocksAar
./gradlew :app:assembleRelease -PskipTun2socksBuild=true
./gradlew versionAuditFail
```

### Update verification metadata
```bash
./gradlew --write-verification-metadata sha256 help
./gradlew --write-verification-metadata sha256 :app:assembleDebug -PskipTun2socksBuild=true
```
