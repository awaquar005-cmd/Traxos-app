# TRAXOS — native Android

Offline-first Kotlin/Jetpack Compose personal finance application. Active native development; **not a finished commercial release**. Source of truth: [V3 specification](docs/Traxos_Native_Android_Product_Blueprint_v3.md).

## Download for phone testing

**[Download TRAXOS 0.12.0 Personal APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.12.0-local-personal.apk)**. Choose **Update**, never uninstall or clear storage. Same package/signature, version code16, Room schema11 unchanged.

Two new local features: password-encrypted financial snapshots with preview/additive restore, and explicit CSV transaction reports. Open **Settings → Backups & recovery → Backup folder** to choose your own folder. The live database stays private in the app. Snapshots cover ledger, accounts, categories, recurring, Plans and portable gift secrets/images; the entire card vault, app lock, pending Smart Add and device preferences are excluded. Keep the password and a copy outside the phone. [Recovery details/limits](docs/LOCAL_RECOVERY.md).

Native screenshot-based Activity, Home monthly/recent/account groups, structured Settings and compact full-screen transaction entry retain the approved hero/logo/watermark, existing features/defaults/Undo and navigation customization. Transfer from Add uses real paired accounting and Undo, without saving the underlying expense draft. No OCR, emulator, cloud SDK or new broad storage/network permission.

**207 tests passed; lint0 errors,21 warnings,1 advisory hint(s).** [Actual validation](docs/validation/local-recovery/README.md) · [Preservation audit](docs/LOCAL_DELIVERY_MANIFEST.json). Physical folder-provider/OEM/IME/TalkBack/process-kill and cross-phone Keystore acceptance remain pending.

Google project/Android OAuth configuration is received and package/SHA-1 registration user-confirmed. Native Drive authorization/upload remains pending; no connected/cloud-backed-up status is simulated. Supabase has not been created and is unnecessary for personal Drive backups. [Configuration and next steps](docs/CLOUD_SETUP.md). Existing encrypted card fields including CVV remain by user instruction; current general ledger is still app-private plaintext. Debug screenshots remain enabled.

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

0.9.2: compact SMS header button hidden at zero; debit/credit card-reference access including after new-account Save; centered compact forms/pickers; clearly labelled Recorded times (not inferred payment times). [Guide](docs/COMPACT_FORMS.md).

0.10.0 adds Accounts → Recurring and an account-owned Transfer dialog, compact cards/report totals/selectors, stable form frames and per-field sensitive card copy. Dashboard/branding unchanged. [Current guide](docs/RECURRING_COMPACT.md).

0.10.1: full-screen routed forms, compact gift wallet/detail layout, concise action rows and development screenshots enabled in DEBUG. Schema10/data unchanged. [Guide](docs/FULLSCREEN_GIFTS.md).

0.11 V8 native pass adds local voice review/hold-release and budget/goal Plans while preserving current finance features and hero. Google/Drive/Supabase integration remains unconfigured, with exact setup instructions. [Status and limitations](docs/V8_STATUS.md) · [Cloud setup](docs/CLOUD_SETUP.md).
