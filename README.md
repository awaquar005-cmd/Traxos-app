# TRAXOS — native Android

Offline-first Kotlin/Jetpack Compose personal finance application. Development milestone 1; **not a finished commercial release**. Source of truth: [V3 specification](docs/Traxos_Native_Android_Product_Blueprint_v3.md).

## Download for phone testing

**0.4.0-gift-wallet adds the local Gift Card & Voucher Wallet and keeps all existing UI/finance fixes. Install as an update over the existing app; do not uninstall or clear storage.**

[Download the test APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.4.0-gift-wallet.apk) · [APK folder and installation help](downloads/README.md).

The repository is available at https://github.com/awaquar005-cmd/Traxos-app. `/workspace/...` paths in development records refer to the cloud workspace, not your laptop. Use the GitHub link to download.

Gift Wallet: manual cards/vouchers, encrypted codes/PINs/notes/images, reviewed text/share imports, bundled offline OCR and QR/barcode photo scanning, exact redemption/history/Undo/refunds and local expiry reminders. Gift Cards and More are optional navigation destinations; your default layout and independent center shortcut are kept. Codes are masked until Reveal; financial gift value stays separate from bank/cash accounting. [Wallet usage, accounting and security limits](docs/GIFT_WALLET.md).

All four screen slots support changing, reordering and reset in Settings → Navigation Customization. Existing dashboard, transactions, account forms, Undo, theme/masking and local persistence are retained. Explicit Room migrations preserve shipped records/preferences. [Gift wallet host previews and validation](docs/validation/gift-wallet/README.md).

## Structure

- `app/src/main/java/app/traxos/finance/domain`: exact money validation/formatting and entry signs.
- `data`: Room entities, indexed queries, paging, repository, atomic immutable operation journal.
- `ui`: branded Compose themes/components, navigation, Home, Transactions, Accounts, Settings and lifecycle ViewModels.
- `gifts`: gift rules/parser, encrypted payloads, Room/repository, transient ViewModel, bundled detection and WorkManager reminders.
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
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.traxos.finance/.MainActivity
```

Windows: use `gradlew.bat` instead of `./gradlew`. Enable Developer options → USB debugging on your physical phone and authorize the computer. `adb devices` must show `device`, not `unauthorized`. No emulator is required or used. Install updates using `adb install -r` with the same signing key; **do not uninstall to update**, because uninstall removes local records. Debug signing is for development only.

This cloud workspace keeps tools under `/workspace/tools`. Its Gradle cache is `/workspace/tools/gradle-home`; use `GRADLE_USER_HOME=/workspace/tools/gradle-home` if the default home is read-only. Cloud proxy/CA settings belong in that local Gradle user configuration, not in the repository. A normal laptop should not copy cloud proxy settings.

Gradle heap: 1.5 GB, two workers, parallel project builds off, in-process Kotlin, single 512 MB test fork, no persistent daemon requested. Run one build at a time and close heavy apps; dependency downloads require internet for the first build. Normal app navigation and bundled recognition have no network dependency; the merged APK has no internet permission. Bundled models increase the universal APK to approximately 74 MiB so first-use detection works offline.

## Development boundaries

Build status: `assembleDebug`, `testDebugUnitTest` (78 tests) and `lintDebug` passed. Lint has 16 dependency/tool-version update notices, no errors. Physical-phone acceptance is pending.

Implemented source covers cash/bank accounts, expense/income CRUD, soft-delete snackbar Undo, SQL month totals, per-currency balances, paged/debounced search, local theme preferences, persisted form fields and operation identity. Read [PROGRESS](docs/PROGRESS.md) for what actually compiled or ran; written code is not equivalent to verified behavior.

No cloud, app PIN, biometrics, encrypted whole database, portable backup, credit cards, transfers, recurring schedules, goals, groups or billing are claimed implemented. Gift codes/PINs/notes/attachments are encrypted with device-bound Keystore keys; merchant/balance/date metadata and the existing ledger remain unencrypted app-private Room. Backups are off; losing/clearing this device loses records. App-private Room is not encrypted. Full production security and recovery must land before public release. WorkManager now runs real local gift expiry reminders; it does not claim cloud synchronization.

## Roadmap

M2 complete offline finance → M3 security and encrypted portable recovery → M4 smart entry/recurrence/reminders → M5 optional encrypted user-owned Drive backup/shared groups → M6 commercial entitlement/update/admin controls → M7 full device, policy and performance hardening. Next: complete the gift wallet physical-device checklist before another feature; then plan the encrypted recovery foundation.
