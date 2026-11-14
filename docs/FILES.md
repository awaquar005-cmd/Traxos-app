# Current delivery

- `downloads/TRAXOS-0.14.0-debts-personal.apk` — Personal code18, update-compatible signature.
- `docs/DEBTS_DELIVERY_MANIFEST.json` — retained routes/old entities/protected sources.
- `docs/validation/debts-credit/` — executed tests/lint/build/signing and native host renders.

# 0.14.0 changes

- `data/Debts.kt`, `ui/DebtsScreen.kt`: Room IOUs, repayments, adjustments/history/Undo, archive/restore and currency-separated totals/card liabilities.
- `data/Database.kt`, `app/schemas/.../12.json`: explicit11→12 additive migration.
- `backup/PortableBackup.kt`: schema12 archive inclusion/validation and retained schema11 compatibility.
- `ui/CloudScreen.kt`, `UiHierarchy.kt`, `Overlays.kt`: padded instructions/controls, actionable Google setup and transient More menu.
- `ui/App.kt`, `Navigation.kt`, `domain/NavigationLayout.kt`: functional More entry and optional configurable destination.
- `DebtsTest.kt`, `CloudScreenUiTest.kt`: accounting/idempotence/migration/recovery and native pointer/form regression checks.
- `app/build.gradle.kts`: code18/0.14.0-debts-personal.

# Current delivery

- `downloads/TRAXOS-0.13.0-cloud-personal.apk` — Personal version17; hash in SHA256SUMS.txt.
- `docs/CLOUD_DELIVERY_MANIFEST.json` — source/route/schema preservation and current service configuration boundaries.
- `docs/validation/oauth-drive/` — successful build log, suite/lint/signature report and synthetic native account/folder PNGs.

# 0.13.0 files

- `cloud/CloudAccounts.kt`: public config importer, encrypted AtomicFile session/owner storage, Firebase auth/refresh/link/verification/reset/password operations.
- `cloud/CloudHttp.kt`: bounded fixed-host TLS requests/streaming files, certificate/package headers and sanitized status handling.
- `cloud/DriveBackups.kt`: stable Google owner, app folders, reserved-ID resumable snapshots/local copy/read-back/restore.
- `backup/FolderStorage.kt`: real SAF grant/provider/probe validation; `ui/BackupScreen.kt` handles actual picker result flags/errors.
- `ui/CloudScreen.kt`, `CloudViewModel.kt`, `CloudSetupScreen.kt`: functional account/provider/Drive setup, binding and recovery flows with custom Traxos controls.
- `TraxosApplication.kt`, `ui/App.kt`, `SettingsScreen.kt`: manual DI, existing profile/Settings entry points and privacy text; local finance remains independent.
- `res/drawable/google_g.png`: official Google asset from https://developers.google.com/static/identity/images/g-logo.png; no Traxos brand asset changed.
- `CloudAccountsTest.kt`, `DriveBackupsTest.kt`, `FolderStorageTest.kt`, `CloudScreenUiTest.kt`: server-contract/security/retry/provider/native UI fixtures. Existing permission assertion now expects INTERNET; transfer test uses dialog-scoped semantic text replacement to avoid host IME focus races.
- `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`: version17/0.13.0-cloud-personal, verified native auth dependencies, explicit internet, CCT backend removal. Room11 unchanged.
- `docs/OAUTH_DRIVE.md`, `CLOUD_SETUP.md`, architecture/decisions/progress: configuration gate, scope/security/limits and exact phone acceptance.

Java/Kotlin paths above are relative to `app/src/main/java/app/traxos/finance/` or `app/src/test/java/app/traxos/finance/`.

# Delivered artifact and validation

- `downloads/TRAXOS-0.12.0-local-personal.apk` — Personal update APK; hash in SHA256SUMS.txt.
- `docs/LOCAL_DELIVERY_MANIFEST.json` — preserved routes/core sources/schema and explicit additions.
- `docs/validation/local-recovery/` — successful log, actual suite/lint/signature metadata and synthetic native PNGs.

# 0.12.0 local recovery and reference UI changes

- `backup/PortableBackup.kt`: authenticated snapshot envelope, portable gift payloads, validated staged/idempotent additive restore and safe CSV writer.
- `ui/BackupScreen.kt`: actual SAF folder/file pickers, persisted grant, password/review/restore/export flow and single-flight ViewModel.
- `ui/App.kt`: recovery route, contextual overflow and preserved native navigation; public cloud setup remains separate.
- `ui/Dashboard.kt`, `LedgerScreens.kt`, `SettingsScreen.kt`: grouped compact reference layout, retained hero, useful icons, live pending review and structured Settings.
- `ui/Forms.kt`, `Controls.kt`, `CatalogComponents.kt`, `Overlays.kt`, `Components.kt`: full-screen entry, flat selectors, quick categories, expandable optional fields, compact footer and bounded scrollable menu.
- `smart/SmartViewModel.kt`: read-only first-pending summary for Home; existing explicit approval writer is reused.
- `ui/CloudSetupScreen.kt`, `docs/CLOUD_SETUP.md`: truthful configuration/transport status; uploaded JSON not committed.
- `PortableBackupTest.kt`, `LocalDeliveryUiTest.kt`: encrypted cross-key round-trip, failures/conflicts/exclusions/CSV and native recovery/UI journeys. Existing journey tests follow the new labels/optional-details location.
- `docs/LOCAL_RECOVERY.md`: coverage, exclusions, cryptography, limits and phone acceptance.
- `app/build.gradle.kts`: version16 /0.12.0-local-personal. Schema11 and dependencies unchanged.

Paths above are relative to `app/src/main/java/app/traxos/finance/` or `app/src/test/java/app/traxos/finance/` where appropriate. Historical file inventories follow.

# Created files

## 0.11.0 changed files

Created voice/VoiceEntry.kt, voice/AndroidOfflineSpeech.kt, ui/VoicePanel.kt, data/Plans.kt, ui/PlansScreen.kt, ui/CloudSetupScreen.kt, Room schema11, five new test classes, original V8 references, preservation/baseline/delivery manifests, setup/status docs and validation. Updated App/Forms/Navigation/NavigationLayout/Home/Components/Theme/Settings/UiHierarchy/Activity/Accounts, Application plans DI, Database explicit migration, microphone permission/service visibility and version metadata. LedgerRepository/Money, gift/QR/image, SMS/Smart repositories, lock/security sources, original brand/splash assets unchanged.

## 0.10.1 UI changes

Also updated BrandedControlsUiTest and NavigationCustomizationUiTest to use concise dialog actions/stable confirmation tags.

Updated Overlays (full-screen form container and concise dialog actions), GiftWalletScreens (compact wallet/full-width consolidated details/short action rows), Forms/CardDetailsScreen/TransferScreen labels, MainActivity debug capture flag, SecurityScreens/SettingsScreen accurate capture descriptions, version and release docs. Updated GiftWalletUiTest/GiftAppearanceTest label and width checks, plus CustomFormsUiTest debug FLAG_SECURE check. No financial/schema/brand changes.

## 0.10.0 changed files

Created data/Recurring.kt, recurring/RecurringWork.kt, ui/RecurringPanel.kt, ui/CardClipboard.kt, schema10, RecurringTest/RecurringMigrationTest/RecurringPanelUiTest/CardClipboardTest and recurring release documentation/validation. Updated Application/MainActivity scheduler wiring, Database migration, Overlays/Forms/GiftWalletScreens/CardDetailsScreen frame/window/copy behavior, Controls/Navigation compact interaction contract, LedgerScreens/Reports/UiHierarchy/App/TransferScreen ownership and build metadata. Dashboard.kt, brand assets and splash resources unchanged.

## 0.9.2 changed files

Updated LedgerScreens (conditional compact SMS action, card details, Recorded timestamp), Forms/ViewModels (save account then optional card editor), CardDetailsScreen, GiftWalletScreens and Overlays (compact centered panels/dialogs), CardVault (BANK/CREDIT_CARD eligibility), Database (query-only Recorded projection), CompactCardTest, version/release documentation. Schema9 and logo assets unchanged.

## 0.9.1 changed files

Updated Dashboard, LedgerScreens, App, SmartInboxScreen, SmartViewModel, SmartParser, SmartRepository and version metadata; added BankMessageParserTest; updated SmartInboxUiTest, release documentation and validation. No Room/schema/logo/dependency change.

## 0.9.0 compact Personal changes

Created `data/CardVault.kt`, `ui/CardDetailsScreen.kt`, Room schema9, `CompactCardTest.kt`, `CardVaultMigrationTest.kt`, `CardDetailsUiTest.kt`, `docs/COMPACT_CARD_PLAN.md`, `docs/COMPACT_PERSONAL.md` and validation/compact-cards. Updated build metadata, TraxosApplication, Database, GiftParser/GiftViewModel, SmartParser, App/Forms, shared Controls/Components/Navigation, Dashboard, LedgerScreens and GiftWalletScreens. Updated release/download documentation and checksum. Existing logo assets unchanged.

## 0.8.0 offline ledger tools

`LedgerToolsScreens.kt` implements filter drafts, deleted recovery and monthly reports; `ActivityScope.kt` defines validated date/ID criteria. Room queries/MVVM add paging/aggregate/sort/restore state without changing financial entities. Schema8 adds only activitySort. Reports joins the real destination registry; More/Settings/Activity expose all completed tools. Shared button borders are half-dp; the header logo explicitly opts out. `traxos_splash_icon.xml` adds Android12 safe-area padding separate from animation. No new SDK/dependency or permission.

Created/modified paths:

- `README.md`
- `app/build.gradle.kts`
- `app/schemas/app.traxos.finance.data.TraxosDatabase/8.json`
- `app/src/main/java/app/traxos/finance/data/Database.kt`
- `app/src/main/java/app/traxos/finance/data/LedgerRepository.kt`
- `app/src/main/java/app/traxos/finance/domain/ActivityScope.kt`
- `app/src/main/java/app/traxos/finance/domain/NavigationLayout.kt`
- `app/src/main/java/app/traxos/finance/ui/App.kt`
- `app/src/main/java/app/traxos/finance/ui/Components.kt`
- `app/src/main/java/app/traxos/finance/ui/Controls.kt`
- `app/src/main/java/app/traxos/finance/ui/LedgerScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/LedgerToolsScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/Navigation.kt`
- `app/src/main/java/app/traxos/finance/ui/Overlays.kt`
- `app/src/main/java/app/traxos/finance/ui/SettingsScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/UiHierarchy.kt`
- `app/src/main/java/app/traxos/finance/ui/ViewModels.kt`
- `app/src/main/res/drawable/traxos_splash_animated.xml`
- `app/src/main/res/drawable/traxos_splash_icon.xml`
- `app/src/test/java/app/traxos/finance/ActivityFiltersUiTest.kt`
- `app/src/test/java/app/traxos/finance/OfflineLedgerToolsTest.kt`
- `app/src/test/java/app/traxos/finance/OfflineToolsMigrationTest.kt`
- `app/src/test/java/app/traxos/finance/OfflineToolsUiTest.kt`
- `app/src/test/java/app/traxos/finance/SplashMaskTest.kt`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/DEVICE_QA.md`
- `docs/FILES.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/OFFLINE_TOOLS.md`
- `docs/OFFLINE_TOOLS_PLAN.md`
- `docs/PROGRESS.md`
- `docs/UI_REVIEW_COVERAGE.md`
- `docs/validation/offline-tools/README.md`
- `docs/validation/offline-tools/build.log`
- `docs/validation/offline-tools/host-results.json`
- `docs/validation/offline-tools/targeted.log`
- `downloads/README.md`
- `downloads/SHA256SUMS.txt`
- `downloads/TRAXOS-0.8.0-personal.apk`
- `downloads/TRAXOS-0.8.0-play.apk`

Validation PNGs created for0.8:

- `docs/validation/offline-tools/dashboard-dark.png`
- `docs/validation/offline-tools/dashboard-light.png`
- `docs/validation/offline-tools/deleted-light.png`
- `docs/validation/offline-tools/more-light.png`
- `docs/validation/offline-tools/reports-dark-masked.png`
- `docs/validation/offline-tools/reports-light.png`
- `docs/validation/offline-tools/settings-dark.png`
- `docs/validation/offline-tools/splash-mask-simulation.png`

Validation PNGs are synthetic host renders or the splash-mask simulation, not physical screenshots. Historical inventories follow.

## 0.7.0 UI review release

New reusable hierarchy/theme/accent/tool rows are in `UiHierarchy.kt`; `ReviewCatalog.kt` defines 100 isolated sample pages and `DesignReviewScreen.kt` renders their state/journey layouts. `TransferScreen.kt` is a real owned-transfer form. Root navigation, Settings/navigation editor, centered unlock, distinct icons/buttons, gift parsing, Smart Add, ledger filters and schema7 are updated in the existing source. Existing transparent brand assets are reused. Test fixtures contain synthetic values only. Validation PNGs in `docs/validation/ui-review/` are synthetic host renders.

Files created or changed in this release:

- `README.md`
- `app/build.gradle.kts`
- `app/schemas/app.traxos.finance.data.TraxosDatabase/7.json`
- `app/src/main/java/app/traxos/finance/MainActivity.kt`
- `app/src/main/java/app/traxos/finance/data/Database.kt`
- `app/src/main/java/app/traxos/finance/data/LedgerRepository.kt`
- `app/src/main/java/app/traxos/finance/domain/Money.kt`
- `app/src/main/java/app/traxos/finance/gifts/BarcodeImageImport.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftParser.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartParser.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartRepository.kt`
- `app/src/main/java/app/traxos/finance/ui/App.kt`
- `app/src/main/java/app/traxos/finance/ui/Components.kt`
- `app/src/main/java/app/traxos/finance/ui/Controls.kt`
- `app/src/main/java/app/traxos/finance/ui/DesignReviewScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/Forms.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftImportSettings.kt`
- `app/src/main/java/app/traxos/finance/ui/LedgerScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/Navigation.kt`
- `app/src/main/java/app/traxos/finance/ui/NavigationSettingsScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/ReviewCatalog.kt`
- `app/src/main/java/app/traxos/finance/ui/SecurityScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/SettingsScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/SmartInboxScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/Theme.kt`
- `app/src/main/java/app/traxos/finance/ui/TransferScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/UiHierarchy.kt`
- `app/src/main/java/app/traxos/finance/ui/ViewModels.kt`
- `app/src/test/java/app/traxos/finance/AccentMigrationTest.kt`
- `app/src/test/java/app/traxos/finance/LockLayoutUiTest.kt`
- `app/src/test/java/app/traxos/finance/NavigationCustomizationUiTest.kt`
- `app/src/test/java/app/traxos/finance/SettingsReentryUiTest.kt`
- `app/src/test/java/app/traxos/finance/SmartAddTest.kt`
- `app/src/test/java/app/traxos/finance/ThemeContrastTest.kt`
- `app/src/test/java/app/traxos/finance/TransferAndImportTest.kt`
- `app/src/test/java/app/traxos/finance/UiAppearanceTest.kt`
- `app/src/test/java/app/traxos/finance/UiReviewTest.kt`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/DEVICE_QA.md`
- `docs/FILES.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/PROGRESS.md`
- `docs/UI_REVIEW.md`
- `docs/UI_REVIEW_COVERAGE.md`
- `docs/UI_REVIEW_PLAN.md`
- `docs/validation/ui-review/README.md`
- `docs/validation/ui-review/build.log`
- `docs/validation/ui-review/host-results.json`
- `docs/validation/ui-review/targeted.log`
- `downloads/README.md`
- `downloads/SHA256SUMS.txt`
- `downloads/TRAXOS-0.7.0-personal.apk`
- `downloads/TRAXOS-0.7.0-play.apk`

Validation PNGs created for this release:

- `docs/validation/ui-review/dashboard-dark.png`
- `docs/validation/ui-review/dashboard-light.png`
- `docs/validation/ui-review/goal-form-preview-dark.png`
- `docs/validation/ui-review/lock-centered-light.png`
- `docs/validation/ui-review/lock-dark-large.png`
- `docs/validation/ui-review/more-light.png`
- `docs/validation/ui-review/navigation-light.png`
- `docs/validation/ui-review/settings-dark.png`

Historical inventory follows.

All files below were created for Milestone 1 in the refreshed workspace. Implementation, status, limitations and commands are documented in README and PROGRESS. SDK/JDK/cache paths and ignored build products are outside this inventory.

- `.gitattributes`
- `.gitignore`
- `README.md`
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/schemas/app.traxos.finance.data.TraxosDatabase/1.json`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/app/traxos/finance/MainActivity.kt`
- `app/src/main/java/app/traxos/finance/TraxosApplication.kt`
- `app/src/main/java/app/traxos/finance/data/Database.kt`
- `app/src/main/java/app/traxos/finance/data/LedgerRepository.kt`
- `app/src/main/java/app/traxos/finance/domain/Money.kt`
- `app/src/main/java/app/traxos/finance/ui/App.kt`
- `app/src/main/java/app/traxos/finance/ui/Theme.kt`
- `app/src/main/java/app/traxos/finance/ui/ViewModels.kt`
- `app/src/main/res/drawable/traxos_mark.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/test/java/app/traxos/finance/FormViewModelTest.kt`
- `app/src/test/java/app/traxos/finance/LedgerRepositoryTest.kt`
- `app/src/test/java/app/traxos/finance/MoneyTest.kt`
- `assets/brand/README.md`
- `assets/brand/traxos-mark.svg`
- `build.gradle.kts`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/DEVICE_QA.md`
- `docs/FILES.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/PROGRESS.md`
- `docs/Traxos_Native_Android_Product_Blueprint_v3.md`
- `docs/validation/build.log`
- `docs/validation/host-results.json`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`
- `gradlew.bat`
- `settings.gradle.kts`

Additional publishing files: `downloads/README.md`, `downloads/TRAXOS-0.1.0-dev.apk`, `downloads/SHA256SUMS.txt`.

Reopen-crash patch modifies `ui/App.kt` (section-scoped lazy keys), app version/test dependencies, version catalog, README/download instructions and maintenance documentation. New host UI tests: `app/src/test/java/app/traxos/finance/HomeStartupTest.kt` and `app/src/test/java/app/traxos/finance/SavedLedgerLaunchTest.kt`. Patch validation evidence is under `docs/validation/reopen/`.

Hotfix artifacts: `downloads/TRAXOS-0.1.1-dev.apk`, updated `downloads/SHA256SUMS.txt`, `docs/validation/reopen/before.txt`, `docs/validation/reopen/build.log`, `docs/validation/reopen/host-results.json`.

UI pass additions: `ui/Components.kt`, `ui/Dashboard.kt`, `ui/LedgerScreens.kt`, `ui/Navigation.kt`, `ui/SettingsScreen.kt`, exported Room schema `2.json`, `PreferencesMigrationTest.kt`, `NavigationUiTest.kt`, `UiAppearanceTest.kt`, `LoadingUiTest.kt`. Modified: App.kt/forms, Theme.kt, ViewModels.kt, Database.kt/preferences DAO/migration, LedgerRepository.kt/partial preference updates, app version/test schema resources, startup tests, architecture/plan/progress/device QA and download documentation. New validation artifacts are under `docs/validation/ui-pass/`.

Published UI-pass artifacts: `downloads/TRAXOS-0.2.0-ui-pass.apk`, checksum update, `docs/validation/ui-pass/{README.md,host-results.json,build.log,dashboard-light.png,dashboard-dark.png}`.

0.3.0 additions: `domain/NavigationLayout.kt`, `ui/Controls.kt`, `ui/Overlays.kt`, `ui/Forms.kt`, `ui/NavigationSettingsScreen.kt`, exported Room schema `3.json`, and `NavigationLayoutTest.kt`, `NavigationPreferencesTest.kt`, `NavigationCustomizationUiTest.kt`, `CustomFormsUiTest.kt`, `BrandedControlsUiTest.kt`. Modified: App/Components/SettingsScreen/Navigation/ViewModels and existing dashboard/list action primitives; Database/LedgerRepository preference support, app version, UI tests and docs. Financial accounting algorithms are unchanged. Validation and synthetic host renders are under `docs/validation/ui-components/` after the final run.

0.3.0 delivery: `downloads/TRAXOS-0.3.0-ui-components.apk`, updated README/checksums, and `docs/validation/ui-components/{README.md,host-results.json,build.log,dashboard-light.png,dashboard-dark.png,transaction-form.png,account-form-dark.png}`.

Gift wallet: `gifts/GiftRules.kt`, `GiftDatabase.kt`, `GiftRepository.kt`, `GiftSecrets.kt`, `GiftReminders.kt`, `GiftImageImport.kt`, `GiftViewModel.kt`; UI `GiftWalletScreens.kt`, `GiftImportSettings.kt`; exported schema `4.json`; tests `GiftRulesTest.kt`, `GiftRepositoryTest.kt`, `GiftMigrationTest.kt`, `GiftReminderWorkerTest.kt`, `GiftWalletUiTest.kt`. Modified: app dependencies/version/manifest, Application/Activity share handling, Room registration/migration, navigation registry/App routes/Settings, shared input password mode, documentation.

Additional gift UI coverage: `GiftAppearanceTest.kt` (light/dark synthetic renders and narrow large-font actions), `GiftNavigationUiTest.kt` (optional Gift Cards/More slots, fallback Settings, restart and permission exclusions). Feature/accounting/security/usage guide: `docs/GIFT_WALLET.md`.

Gift release artifacts: `downloads/TRAXOS-0.4.0-gift-wallet.apk`, README/checksum updates, `docs/validation/gift-wallet/{README.md,host-results.json,build.log,test-run.log,gift-wallet-light.png,gift-wallet-dark.png}`. Existing schemas 1/2/3 and financial accounting sources retained.


## 0.5.0 smart import/security/settings pass

Current guide: [SMART_SECURITY.md](SMART_SECURITY.md). Added/modified source/configuration/tests and documentation:

- `.gitignore`
- `README.md`
- `app/build.gradle.kts`
- `app/schemas/app.traxos.finance.data.TraxosDatabase/5.json`
- `app/src/androidTest/java/app/traxos/finance/OcrCorpusInstrumentedTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/app/traxos/finance/MainActivity.kt`
- `app/src/main/java/app/traxos/finance/TraxosApplication.kt`
- `app/src/main/java/app/traxos/finance/data/Database.kt`
- `app/src/main/java/app/traxos/finance/data/LedgerRepository.kt`
- `app/src/main/java/app/traxos/finance/domain/NavigationLayout.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftImageImport.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftParser.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftRepository.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftRules.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftSecrets.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftViewModel.kt`
- `app/src/main/java/app/traxos/finance/gifts/OcrReadingOrder.kt`
- `app/src/main/java/app/traxos/finance/security/DeviceAuthentication.kt`
- `app/src/main/java/app/traxos/finance/security/LockStore.kt`
- `app/src/main/java/app/traxos/finance/security/LockViewModel.kt`
- `app/src/main/java/app/traxos/finance/smart/FinancialSmsReceiver.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartDatabase.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartParser.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartRepository.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartViewModel.kt`
- `app/src/main/java/app/traxos/finance/smart/SmsAccess.kt`
- `app/src/main/java/app/traxos/finance/ui/App.kt`
- `app/src/main/java/app/traxos/finance/ui/Controls.kt`
- `app/src/main/java/app/traxos/finance/ui/Forms.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftCropSheet.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftImportSettings.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftWalletScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/Navigation.kt`
- `app/src/main/java/app/traxos/finance/ui/Overlays.kt`
- `app/src/main/java/app/traxos/finance/ui/SecurityScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/SettingsScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/SmartInboxScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/ViewModels.kt`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/xml/gift_capture_paths.xml`
- `app/src/smsApproved/AndroidManifest.xml`
- `app/src/test/java/app/traxos/finance/GiftContextParserTest.kt`
- `app/src/test/java/app/traxos/finance/GiftImagePreparationTest.kt`
- `app/src/test/java/app/traxos/finance/GiftRulesTest.kt`
- `app/src/test/java/app/traxos/finance/GiftWalletUiTest.kt`
- `app/src/test/java/app/traxos/finance/LockSessionTest.kt`
- `app/src/test/java/app/traxos/finance/LockStoreTest.kt`
- `app/src/test/java/app/traxos/finance/NavigationCustomizationUiTest.kt`
- `app/src/test/java/app/traxos/finance/NavigationUiTest.kt`
- `app/src/test/java/app/traxos/finance/PreferenceIsolationTest.kt`
- `app/src/test/java/app/traxos/finance/SecurityUiTest.kt`
- `app/src/test/java/app/traxos/finance/SmartAddTest.kt`
- `app/src/test/java/app/traxos/finance/SmartInboxUiTest.kt`
- `app/src/test/java/app/traxos/finance/SmartMigrationTest.kt`
- `app/src/test/java/app/traxos/finance/SmartParserTest.kt`
- `app/src/test/java/app/traxos/finance/StructuredSettingsUiTest.kt`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/DEVICE_QA.md`
- `docs/GIFT_WALLET.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/PROGRESS.md`
- `docs/SMART_SECURITY.md`
- `docs/SMART_SECURITY_PLAN.md`
- `downloads/README.md`
- `gradle/libs.versions.toml`

Delivery also includes schema5, downloads/TRAXOS-0.5.0-smart-security.apk, checksums and docs/validation/smart-security build/test/packaging evidence. Native instrumentation harness is compiled but not run; its private fixture folder is ignored.


## 0.6.0 UX, smart entry and security changes

[Current guide](UX_SMART_ENTRY.md) · [actual validation](validation/ux-refinement/README.md). Includes new/modified paths and removed OCR/obsolete SMS sources. No generated SDK/cache/signing keys are included.

- `.gitignore`
- `README.md`
- `app/build.gradle.kts`
- `app/schemas/app.traxos.finance.data.TraxosDatabase/6.json`
- `app/src/androidTest/java/app/traxos/finance/OcrCorpusInstrumentedTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/app/traxos/finance/MainActivity.kt`
- `app/src/main/java/app/traxos/finance/data/Database.kt`
- `app/src/main/java/app/traxos/finance/data/LedgerRepository.kt`
- `app/src/main/java/app/traxos/finance/domain/Money.kt`
- `app/src/main/java/app/traxos/finance/gifts/BarcodeImageImport.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftImageImport.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftParser.kt`
- `app/src/main/java/app/traxos/finance/gifts/OcrReadingOrder.kt`
- `app/src/main/java/app/traxos/finance/security/LockViewModel.kt`
- `app/src/main/java/app/traxos/finance/smart/FinancialSmsReceiver.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartParser.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartRepository.kt`
- `app/src/main/java/app/traxos/finance/smart/SmsAccess.kt`
- `app/src/main/java/app/traxos/finance/ui/App.kt`
- `app/src/main/java/app/traxos/finance/ui/CatalogComponents.kt`
- `app/src/main/java/app/traxos/finance/ui/Components.kt`
- `app/src/main/java/app/traxos/finance/ui/Controls.kt`
- `app/src/main/java/app/traxos/finance/ui/Dashboard.kt`
- `app/src/main/java/app/traxos/finance/ui/Forms.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftCropSheet.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftImportSettings.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftWalletScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/LedgerScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/Overlays.kt`
- `app/src/main/java/app/traxos/finance/ui/SecurityScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/SettingsScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/SmartInboxScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/Theme.kt`
- `app/src/main/java/app/traxos/finance/ui/ViewModels.kt`
- `app/src/main/res/animator/brand_enter.xml`
- `app/src/main/res/drawable/traxos_splash.xml`
- `app/src/main/res/drawable/traxos_splash_animated.xml`
- `app/src/main/res/drawable/traxos_splash_mark.xml`
- `app/src/main/res/values-v31/styles.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/personal/AndroidManifest.xml`
- `app/src/personal/java/app/traxos/finance/smart/FinancialSmsReceiver.kt`
- `app/src/personal/java/app/traxos/finance/smart/SmsIngestion.kt`
- `app/src/personal/java/app/traxos/finance/ui/SmsSettings.kt`
- `app/src/play/AndroidManifest.xml`
- `app/src/play/java/app/traxos/finance/ui/SmsSettings.kt`
- `app/src/smsApproved/AndroidManifest.xml`
- `app/src/test/java/app/traxos/finance/CatalogAccountingTest.kt`
- `app/src/test/java/app/traxos/finance/CatalogInlineUiTest.kt`
- `app/src/test/java/app/traxos/finance/CatalogMigrationTest.kt`
- `app/src/test/java/app/traxos/finance/CustomFormsUiTest.kt`
- `app/src/test/java/app/traxos/finance/GiftImagePreparationTest.kt`
- `app/src/test/java/app/traxos/finance/GiftNavigationUiTest.kt`
- `app/src/test/java/app/traxos/finance/LockLayoutUiTest.kt`
- `app/src/test/java/app/traxos/finance/LockSessionTest.kt`
- `app/src/test/java/app/traxos/finance/SecurityUiTest.kt`
- `app/src/test/java/app/traxos/finance/SettingsReentryUiTest.kt`
- `app/src/test/java/app/traxos/finance/SmartAddTest.kt`
- `app/src/test/java/app/traxos/finance/SmartInboxUiTest.kt`
- `app/src/test/java/app/traxos/finance/SmartParserTest.kt`
- `app/src/test/java/app/traxos/finance/StructuredSettingsUiTest.kt`
- `app/src/test/java/app/traxos/finance/ThemeContrastTest.kt`
- `app/src/testPersonal/java/app/traxos/finance/SmsIngestionTest.kt`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/DEVICE_QA.md`
- `docs/GIFT_WALLET.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/PROGRESS.md`
- `docs/SMART_SECURITY.md`
- `docs/UX_SMART_ENTRY.md`
- `docs/UX_SMART_REFINEMENT.md`
- `docs/validation/ux-refinement/README.md`
- `docs/validation/ux-refinement/build.log`
- `docs/validation/ux-refinement/host-results.json`
- `docs/validation/ux-refinement/lock-dark-large.png`
- `docs/validation/ux-refinement/settings-dark.png`
- `docs/validation/ux-refinement/stage1.log`
- `docs/validation/ux-refinement/stage2.log`
- `docs/validation/ux-refinement/stage3.log`
- `docs/validation/ux-refinement/stage4.log`
- `docs/validation/ux-refinement/stage5.log`
- `downloads/README.md`
- `downloads/SHA256SUMS.txt`
- `downloads/TRAXOS-0.6.0-personal.apk`
- `downloads/TRAXOS-0.6.0-play.apk`
- `gradle/libs.versions.toml`
