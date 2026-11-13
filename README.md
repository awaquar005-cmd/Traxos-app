# TRAXOS — native Android

Offline-first Kotlin/Jetpack Compose personal finance application. Active native development; **not a finished commercial release**. Source of truth: [V3 specification](docs/Traxos_Native_Android_Product_Blueprint_v3.md).

## Download for phone testing

**[Download TRAXOS 0.13.0 Personal APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.13.0-cloud-personal.apk)**. Choose **Update**, never uninstall or clear storage. Same package/signature, version code17, Room schema11 unchanged.

New native Drive OAuth, fixed Google owner linking (including local-only users), app-owned folder selection, password-encrypted resumable snapshots, optional verified local copy, remote downloaded/authenticated verification and reviewed additive restore. Local SAF now validates actual read/write/persistable grants and create/read/delete capability before reporting a selected folder. Existing approved UI/hero/brand and financial/gift/QR/images/Plans/recurring/security/defaults/navigation are preserved. [Flow and exact configuration](docs/OAUTH_DRIVE.md).

**Email/password and Google account sign-up/sign-in/provider linking are implemented but need the owner's Firebase Authentication configuration.** No Firebase API key/Web client file has been supplied, so these providers show setup-required states until public google-services.json is imported. Drive-only local-user linking uses the existing Android OAuth client independently. No actual owner account login, verification/reset email or Drive upload has been live-tested. Supabase/group sync is separate. No login/backup success is simulated.

**232 host tests passed; lint0 errors,23 warnings,1 hint(s).** [Actual evidence](docs/validation/oauth-drive/README.md) · [Preservation audit](docs/CLOUD_DELIVERY_MANIFEST.json). Physical Google/phone/provider/Keystore/OEM/IME/network acceptance remains pending; no emulator.

Snapshots exclude the entire card vault, offline PIN, cloud sessions/bindings, pending Smart Add and device preferences. Keep the archive password and a copy outside the phone. [Recovery coverage/limits](docs/LOCAL_RECOVERY.md). General ledger remains app-private plaintext; DEBUG screenshots stay enabled. INTERNET is new for explicit online actions; no Analytics/Firebase database SDK or OCR is added. Single-module conservative Gradle configuration retained.

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
