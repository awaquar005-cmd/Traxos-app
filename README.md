# TRAXOS — native Android

Offline-first Kotlin/Jetpack Compose personal finance application. Development milestone 1; **not a finished commercial release**. Source of truth: [V3 specification](docs/Traxos_Native_Android_Product_Blueprint_v3.md).

## Download for phone testing

**0.2.0-ui-pass refines the dashboard/navigation and keeps the reopen-crash fix. Install as an update over the existing app; do not uninstall or clear storage.**

[Download the test APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.2.0-ui-pass.apk) · [APK folder and installation help](downloads/README.md).

The repository is available at https://github.com/awaquar005-cmd/Traxos-app. `/workspace/...` paths in development records refer to the cloud workspace, not your laptop. Use the GitHub link to download.

New UI: custom navy balance hero/watermark, compact navigation, real loading skeletons and Settings-configured center shortcut. Balance visibility is persisted and applies to dashboard/lists. Finance scope remains unchanged. Room schema 2 safely upgrades preference fields from v1. [Light/dark host previews and validation](docs/validation/ui-pass/README.md).

## Structure

- `app/src/main/java/app/traxos/finance/domain`: exact money validation/formatting and entry signs.
- `data`: Room entities, indexed queries, paging, repository, atomic immutable operation journal.
- `ui`: branded Compose themes/components, navigation, Home, Transactions, Accounts, Settings and lifecycle ViewModels.
- `app/src/test`: money unit tests and Robolectric Room/repository tests (no emulator).
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
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.traxos.finance/.MainActivity
```

Windows: use `gradlew.bat` instead of `./gradlew`. Enable Developer options → USB debugging on your physical phone and authorize the computer. `adb devices` must show `device`, not `unauthorized`. No emulator is required or used. Install updates using `adb install -r` with the same signing key; **do not uninstall to update**, because uninstall removes local records. Debug signing is for development only.

This cloud workspace keeps tools under `/workspace/tools`. Its Gradle cache is `/workspace/tools/gradle-home`; use `GRADLE_USER_HOME=/workspace/tools/gradle-home` if the default home is read-only. Cloud proxy/CA settings belong in that local Gradle user configuration, not in the repository. A normal laptop should not copy cloud proxy settings.

Gradle heap: 1.5 GB, two workers, parallel project builds off, in-process Kotlin, single 512 MB test fork, no persistent daemon requested. Run one build at a time and close heavy apps; dependency downloads require internet for the first build. Normal app navigation has no network dependency or internet permission.

## Development boundaries

Build status: `assembleDebug`, `testDebugUnitTest` (35 tests) and `lintDebug` passed. Lint has 14 dependency-version update notices, no errors. Physical-phone acceptance is pending.

Implemented source covers cash/bank accounts, expense/income CRUD, soft-delete snackbar Undo, SQL month totals, per-currency balances, paged/debounced search, local theme preferences, persisted form fields and operation identity. Read [PROGRESS](docs/PROGRESS.md) for what actually compiled or ran; written code is not equivalent to verified behavior.

No cloud, PIN, biometrics, encrypted database, portable backup, cards, transfers, recurring schedules, goals, groups or billing are claimed implemented. Backups are off; losing/clearing this device loses records. App-private Room is not encrypted. Full production security and recovery must land before public release. WorkManager will be introduced with real reminders/backup jobs rather than a fake sync worker.

## Roadmap

M2 complete offline finance → M3 security and encrypted portable recovery → M4 smart entry/recurrence/reminders → M5 optional encrypted user-owned Drive backup/shared groups → M6 commercial entitlement/update/admin controls → M7 full device, policy and performance hardening. Recommended next work after verifying M1: perform physical phone acceptance, then paired-posting transfers/card semantics and local recovery foundation.
