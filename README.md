# TRAXOS — native Android

Offline-first Kotlin/Jetpack Compose personal finance application. Development milestone 1; **not a finished commercial release**. Source of truth: [V3 specification](docs/Traxos_Native_Android_Product_Blueprint_v3.md).

## Personal design experiment

**[Download the Unslop Personal experiment APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/experiment/unslop-ui/downloads/TRAXOS-0.8.0-unslop-personal.apk)**. This branch is an experiment; main retains the validated0.8.0 release.

Compare in **Settings → Appearance & Navigation → Experimental interface**. Turn it off to return to the previous styling with the latest borderless-button corrections. All0.8 financial tools stay working. Logo, watermark, splash, hero eye and meaningful icons are retained. No new dashboard widgets, images, animations, SDKs or Room migration. Back/Add Account/overflow and other actions have no outline and retain feedback. Only the Personal experiment is built.

Install as **Update**, never uninstall/clear storage. Same package, signing key, version code9 and schema8 as the baseline. [Audit, return instructions and Unslop attribution](docs/UNSLOP_EXPERIMENT.md) · [Actual Personal validation](docs/validation/unslop-experiment/README.md).

## Preserved baseline download

**[Download TRAXOS 0.8.0 Personal APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.8.0-personal.apk)**. Install as **Update**; do not uninstall or clear storage. Your records, app PIN, gift secrets and navigation preferences are preserved by the signed update and explicit Room migration.

[SMS-free Play build](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.8.0-play.apk) · [Installation help/checksums](downloads/README.md) · [Current guide and limitations](docs/OFFLINE_TOOLS.md) · [Actual tests/lint/previews](docs/validation/offline-tools/README.md).


This release adds **real Activity filters and saved ordering, monthly category reports with drill-through, and deleted-transaction restore/Undo**. Reports separate currencies and exclude transfers/card payments; linked movements restore atomically. Find the new tools in More, Activity's menu and Settings → Accounts & Data. [Behavior and edge cases](docs/OFFLINE_TOOLS.md).

The splash mark now fits Android12's icon mask with all five squares intact. The logo has no outline; action buttons use minimal half-dp borders. Existing financial data, UI themes/navigation, gifts and security remain. More → Explore all screen flows still opens 100 explicitly labeled sample pages for future V3 hierarchy review. [Review inventory](docs/UI_REVIEW_COVERAGE.md).

OCR is removed; QR/barcodes, manual/text gift entry, reminders and redemption remain. Custom categories/accounts, inline creation, saved entry options, compact forms, automatic biometric prompting, a branded PIN/splash, compact menus and Settings fresh-entry navigation are implemented. Credit-card charges/refunds/paired payments use signed debt and do not count credit limits or repayments as income/expense.

Personal SMS: Settings → Smart Tracking → Incoming financial SMS → disclosure → Android permission. Only future incoming eligible messages create encrypted review suggestions; approval is required. Android/installer restrictions can deny access even for sideloaded apps; share/paste is always available. Play excludes SMS permissions and receiver code. Neither build reads historical SMS or uploads financial data. App lock does not encrypt the ledger; no portable recovery/backup exists yet. Actual native biometrics/SMS/phone acceptance is pending.

The repository is https://github.com/awaquar005-cmd/Traxos-app. `/workspace/...` paths refer to the cloud workspace, not your laptop; use the download link.

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
