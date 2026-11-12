# TRAXOS — native Android

Offline-first Kotlin/Jetpack Compose personal finance application. Development milestone 1; **not a finished commercial release**. Source of truth: [V3 specification](docs/Traxos_Native_Android_Product_Blueprint_v3.md).

## Download for phone testing

**[Download TRAXOS 0.9.1 Personal APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.9.1-personal.apk)**. Choose **Update**, never uninstall or clear storage. Same package/signature, code11 and safe Room migration preserve records.

Previous / Unslop OFF design, tighter dashboard/Activity spacing, borderless actions, improved gift text detection/defaults and saved merchant choices. Optional encrypted card number/CVV/expiry references are under Edit credit-card account. Card purchases/repayments retain correct liability accounting. Only Personal is built for this release.

[Current behavior and limitations](docs/COMPACT_PERSONAL.md) · [Actual validation](docs/validation/sms-quick-review/README.md) · [Installation help](downloads/README.md). App lock does not encrypt the general ledger. There is no portable Keystore/PIN recovery. Physical-phone acceptance is pending; no emulator used. User review is required for Smart Add; OCR remains removed. Existing future-module gallery remains explicitly sample-only.

## Structure

- `app/src/main/java/app/traxos/finance/domain`: exact money validation/formatting and entry signs.
- `data`: Room entities, indexed queries, paging, repository, atomic immutable operation journal.
- `ui`: branded Compose themes/components, navigation, Home, Transactions, Accounts, Settings and lifecycle ViewModels.
- `security`: encrypted PIN configuration, throttling, app gate and native biometric challenge.
- `smart`: local classification, encrypted review inbox, atomic approval and policy-gated receive-only adapter.
- `gifts`: gift rules/parser, encrypted payloads, Room/repository, transient ViewModel, independent bundled barcode detection (no OCR) and WorkManager reminders.
- `app/src/test`: money, Room/repository, migration, crypto envelope, WorkManager and native Compose host tests (no emulator).
- `app/schemas`: generated Room schema history after a successful KSP build. Commit every schema; require tested forward migration for changes.
- `assets/brand`: transparent provisional five-square SVG and provenance note; native VectorDrawable is in app resources.
- `docs`: plan with full requirement inventory, architecture, decisions, actual progress and physical QA checklist.

## Build on an 8 GB laptop

Use JDK 17 or 21, Android SDK command-line tools, Android platform 36, Build Tools 36.0.0 and platform-tools. AGP 8.10.1, Gradle 8.11.1 and Kotlin 2.1.20 are pinned. Dependency versions are in `gradle/libs.versions.toml`; the Gradle wrapper distribution checksum is pinned. Accept Google SDK licenses after reviewing the displayed terms.

Configure `local.properties` with your real SDK path (never commit it):

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Linux/macOS:

```sh
cd /workspace/apk-development  # replace with your checkout path on your laptop
./gradlew --no-daemon :app:assemblePersonalDebug :app:testPersonalDebugUnitTest :app:lintPersonalDebug
adb devices
adb install -r app/build/outputs/apk/personal/debug/app-personal-debug.apk
adb shell am start -n app.traxos.finance/.MainActivity
```

Windows: use `gradlew.bat` instead of `./gradlew`. Enable Developer options → USB debugging on your physical phone and authorize the computer. `adb devices` must show `device`, not `unauthorized`. No emulator is required or used. Install updates using `adb install -r` with the same signing key; **do not uninstall to update**, because uninstall removes local records. Debug signing is for development only.

This cloud workspace keeps tools under `/workspace/tools`. Its Gradle cache is `/workspace/tools/gradle-home`; use `GRADLE_USER_HOME=/workspace/tools/gradle-home` if the default home is read-only. Cloud proxy/CA settings belong in that local Gradle user configuration, not in the repository. A normal laptop should not copy cloud proxy settings.

Gradle heap: 1.5 GB, two workers, parallel project builds off, in-process Kotlin, single 512 MB test fork, no persistent daemon requested. Run one build at a time and close heavy apps; dependency downloads require internet for the first build. Normal navigation and bundled QR/barcode scanning need no network; neither APK has internet permission. Removing OCR reduced each universal APK to approximately 34.8 MiB.

## Development boundaries

Both debug variants build and pass lint with zero errors. Personal: **128 host tests passed**, 20 lint warnings (19 version advisories and one KTX style suggestion). Play: **126 host tests passed**, 19 version advisories. No failures/errors/skips. Physical-phone acceptance remains pending; these are host results.

Implemented scope includes existing finance/gift features, custom entry catalogs and inline creation, basic card liability/repayment accounting, encrypted reviewed Smart Add, offline PIN/native biometric app locking and customizable navigation. [PROGRESS](docs/PROGRESS.md) records actual validation and remaining work.

Cloud sync, portable backup/export, whole-database encryption, general transfers, card statement/interest automation, recurring schedules, goals, groups and billing remain future work. Gift secrets, Smart Add payloads and PIN security state use separate device-bound encryption; ledger and metadata remain app-private plaintext. There is no portable recovery or PIN-reset bypass. WorkManager runs real local gift expiry reminders, not cloud synchronization.

## Roadmap

Next: complete the [physical acceptance checklist](docs/DEVICE_QA.md), including upgrade preservation, biometrics/credentials, installer-restricted SMS and accessibility. Fix measured issues before another feature. Encrypted portable recovery remains a separate future milestone; the full V3 roadmap is not complete.

0.9.1: restored only dashboard hero proportions; direct card-details access in Accounts; Activity SMS pending indicator; inline Approve/Reject and Edit-only sheet; improved bank SMS fields. [Quick-review guide](docs/SMS_QUICK_REVIEW.md).
